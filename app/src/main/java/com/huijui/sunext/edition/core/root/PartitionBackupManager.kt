package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PartitionBackupManager {

    data class BackupResult(val success: Boolean, val backupPath: String, val message: String)
    data class BackupInfo(val partition: String, val path: String, val fileName: String, val sizeBytes: Long, val timestamp: Long)

    private const val BACKUP_DIR = FlashGuard.BACKUP_DIR
    private const val MAX_VERSIONS = FlashGuard.MAX_BACKUP_VERSIONS
    private val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val RESTORE_ALLOWED = setOf("boot", "init_boot", "vendor_boot", "dtbo")

    val BACKUPABLE_PARTITIONS: List<Pair<String, String>> = listOf(
        "boot" to "boot（启动分区）", "init_boot" to "init_boot（A13+ 启动）",
        "vendor_boot" to "vendor_boot（厂商启动）", "dtbo" to "dtbo（设备树覆盖）",
        "persist" to "persist（设备身份）", "persistbak" to "persistbak（身份备份）",
        "modem" to "modem（基带）", "modem_1" to "modem_1（基带槽1）",
        "modem_2" to "modem_2（基带槽2）", "radio" to "radio（无线）",
        "fsg" to "fsg（文件系统）", "fsc" to "fsc（文件系统计数）",
        "nvram" to "nvram（NVRAM）", "nvdata" to "nvdata（网络数据）",
        "nvcfg" to "nvcfg（网络配置）", "protect1" to "protect1（保护分区）",
        "protect2" to "protect2（保护分区）"
    )

    fun canRestore(partition: String): Boolean = partition.trim().lowercase() in RESTORE_ALLOWED

    suspend fun backupPartition(partition: String): BackupResult = withContext(Dispatchers.IO) {
        val cleanPartition = partition.trim().lowercase()
        if (cleanPartition.isBlank()) return@withContext BackupResult(false, "", "分区名无效")
        val devicePath = resolvePartitionDevice(cleanPartition) ?: return@withContext BackupResult(false, "", "找不到设备节点")
        HuijuiShell.exec("mkdir -p $BACKUP_DIR && chmod 700 $BACKUP_DIR")
        
        val timestamp = timeFormat.format(Date())
        val backupPath = "$BACKUP_DIR/${cleanPartition}_${timestamp}.img"
        // conv=fsync + sync：不落盘就拔电/重启，备份文件是半截的，恢复时直接写坏分区
        val ddRes = HuijuiShell.exec(
            "dd if='$devicePath' of='$backupPath' bs=1M conv=fsync 2>&1; sync",
            timeoutMs = 120_000L
        )
        if (!ddRes.isSuccess) {
            HuijuiShell.exec("rm -f '$backupPath'")
            return@withContext BackupResult(false, "", "dd 备份失败")
        }

        val checkRes = HuijuiShell.exec("test -s '$backupPath' && stat -c %s '$backupPath' 2>/dev/null || echo 0")
        val size = checkRes.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        if (size <= 0L) {
            HuijuiShell.exec("rm -f '$backupPath'")
            return@withContext BackupResult(false, "", "备份文件大小异常")
        }

        HuijuiShell.exec("chmod 600 '$backupPath'")
        pruneOldBackups(cleanPartition)
        BackupResult(true, backupPath, "已备份 $cleanPartition (${size / 1024 / 1024}MB)")
    }

    suspend fun restorePartition(partition: String, backupPath: String): Boolean = withContext(Dispatchers.IO) {
        val cleanPartition = partition.trim().lowercase()
        if (cleanPartition !in RESTORE_ALLOWED || backupPath.isBlank()) return@withContext false

        // 归属校验：文件名必须是 <partition>_…，防止把 boot 的备份恢复进 dtbo
        val fileName = backupPath.substringAfterLast('/')
        if (!fileName.startsWith("${cleanPartition}_")) return@withContext false

        val devicePath = resolvePartitionDevice(cleanPartition) ?: return@withContext false

        // 复用 InstallIo.writeImage：它会做容量校验、conv=fsync、sync、以及**全长**回读 MD5。
        // 旧实现只有一句裸 dd，既没有容量校验也没有回读 —— 备份比分区大时会写坏相邻区域。
        InstallIo.writeImage(backupPath, devicePath).ok
    }

    suspend fun listAllBackups(): Map<String, List<BackupInfo>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, MutableList<BackupInfo>>()
        val lsRes = HuijuiShell.exec("ls -1 $BACKUP_DIR 2>/dev/null")
        if (!lsRes.isSuccess) return@withContext emptyMap()

        for (fileName in lsRes.out) {
            if (fileName.isBlank() || !fileName.endsWith(".img")) continue
            val parts = fileName.removeSuffix(".img").split("_")
            if (parts.size < 3) continue
            val partition = parts.dropLast(2).joinToString("_")
            
            val path = "$BACKUP_DIR/$fileName"
            val statRes = HuijuiShell.exec("stat -c '%s %Y' '$path' 2>/dev/null")
            val statParts = statRes.out.firstOrNull()?.trim()?.split(" ") ?: continue
            val size = statParts.getOrNull(0)?.toLongOrNull() ?: 0L
            val mtime = statParts.getOrNull(1)?.toLongOrNull() ?: 0L

            result.getOrPut(partition) { mutableListOf() }.add(BackupInfo(partition, path, fileName, size, mtime * 1000))
        }
        result.mapValues { it.value.sortedByDescending { b -> b.timestamp } }
    }

    suspend fun deleteBackup(backupPath: String): Boolean = withContext(Dispatchers.IO) {
        if (backupPath.isBlank() || !backupPath.startsWith("$BACKUP_DIR/") || !backupPath.endsWith(".img")) return@withContext false
        HuijuiShell.exec("rm -f '$backupPath' && echo ok").out.joinToString("").contains("ok")
    }

    suspend fun pruneOldBackups(partition: String) = withContext(Dispatchers.IO) {
        HuijuiShell.exec("ls -1t $BACKUP_DIR/${partition}_*.img 2>/dev/null | tail -n +$((MAX_VERSIONS + 1)) | xargs -r rm -f")
        Unit
    }

    /**
     * 分区名 → 设备节点。
     *
     * A/B 设备上必须先试**当前槽位**：旧实现固定按 boot → boot_a → boot_b 找，
     * 在 _b 槽上运行时会把 _a 当成目标 —— 备份备错槽、恢复写进另一个槽，
     * 表现为「恢复成功但开机还是旧系统」。
     */
    private suspend fun resolvePartitionDevice(partition: String): String? {
        val slot = runCatching {
            HuijuiShell.exec("getprop ro.boot.slot_suffix").out.firstOrNull()?.trim().orEmpty()
        }.getOrDefault("")

        val candidates = buildList {
            if (slot.isNotBlank()) add("/dev/block/by-name/$partition$slot")
            add("/dev/block/by-name/$partition")
            add("/dev/block/by-name/${partition}_a")
            add("/dev/block/by-name/${partition}_b")
        }.distinct()

        for (c in candidates) {
            if (HuijuiShell.exec("test -e '$c' && echo yes || echo no").out.joinToString("").contains("yes")) {
                return c
            }
        }
        return null
    }
}
