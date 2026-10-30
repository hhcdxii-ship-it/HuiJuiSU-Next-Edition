package com.huijui.sunext.edition.core.root

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 🛡️ HUI 防格机 / 防格基带硬性保护层
 *
 * 设计目标：任何"刷入"操作之前，物理拦截一切可能变砖或丢数据的路径。
 *
 * 三层防护：
 *   1. 白名单：仅 boot / init_boot / vendor_boot / dtbo 允许刷入
 *   2. 黑名单：基带 / 校准 / 系统数据 / Bootloader / TEE 全部物理拦截
 *   3. 大小上限：每个分区有明确上限，防误把大文件刷进小分区
 *
 * 环境预检：
 *   - 电量 ≥ 30%
 *   - 备份目录可写
 *   - A/B slot 提示
 *   - 高级模式开关
 *
 * 使用约定：
 *   - preflightEnvironment(context) 每次刷入前调一次
 *   - validatePartition(targetPartition) 每个任务 detect 后调
 *   - validateFileSize(filePath, partition) 刷入前调
 */
object FlashGuard {

    /** 白名单：仅这些分区允许刷入 */
    private val ALLOWED_PARTITIONS = setOf(
        "boot",
        "init_boot",
        "vendor_boot",
        "dtbo"
    )

    /**
     * 黑名单：物理拦截，永远不允许刷入
     *
     * 分类整理，方便未来扩展：
     * - 基带 / 无线：刷错直接丢信号、无 IMEI
     * - 设备身份 / 校准：刷错指纹失效、保修信息丢失
     * - 系统数据：刷错丢用户数据、系统无法启动
     * - Bootloader / TEE：刷错直接变砖
     */
    private val FORBIDDEN_PARTITIONS = setOf(
        // ===== 基带 / 无线 =====
        "modem", "modem_1", "modem_2",
        "radio", "fsg", "fsc",
        "nvram", "nvdata", "nvcfg", "nvdata_backup", "nvdata_bak",
        // ===== 设备身份 / 校准 =====
        "persist", "persistbak", "persist_bak",
        "protect1", "protect2",
        "frp", "efs", "sec_efs", "modemst1", "modemst2",
        // ===== 系统数据 =====
        "userdata", "metadata", "cache", "misc",
        "super", "system", "system_ext", "vendor", "product", "odm",
        "my_product", "my_stock", "my_heytap", "my_carrier", "my_region",
        "oem", "preload", "cust",
        // ===== Bootloader / TEE =====
        "abl", "xbl", "xbl_config", "tz", "hyp", "keymaster", "devcfg",
        "cmnlib", "cmnlib64", "mdtp", "mdtpsecapp", "sbl1",
        "devinfo", "apdp", "msadp", "dip"
    )

    /** 大小上限（字节） */
    private val SIZE_LIMITS = mapOf(
        "boot" to 128L * 1024 * 1024,
        "init_boot" to 128L * 1024 * 1024,
        "vendor_boot" to 128L * 1024 * 1024,
        "dtbo" to 32L * 1024 * 1024
    )

    /** 最小电量 */
    private const val MIN_BATTERY_PERCENT = 30

    /** 备份目录 */
    const val BACKUP_DIR = "/data/adb/huijuisu/backup"

    /** 备份保留份数（段 B 用） */
    const val MAX_BACKUP_VERSIONS = 3

    data class PreflightResult(
        val passed: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    data class PartitionValidation(
        val allowed: Boolean,
        val reason: String
    )

    /**
     * 🚨 环境预检（每次刷入前调一次）
     */
    suspend fun preflightEnvironment(
        context: Context,
        advancedMode: Boolean
    ): PreflightResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. 电量检查
        val battery = readBatteryPercent(context)
        if (battery in 0..(MIN_BATTERY_PERCENT - 1)) {
            errors.add("电量不足（$battery% < $MIN_BATTERY_PERCENT%），刷入中没电会损坏分区")
        } else if (battery in MIN_BATTERY_PERCENT..39) {
            warnings.add("电量较低（$battery%），建议充到 50% 以上再刷")
        }

        // 2. 备份目录可写（段 B 建备份用）
        val mkdirRes = HuijuiShell.exec(
            "su -c 'mkdir -p $BACKUP_DIR && test -w $BACKUP_DIR && echo ok || echo fail'"
        )
        if (!mkdirRes.out.joinToString("").contains("ok")) {
            warnings.add("备份目录 $BACKUP_DIR 不可写，无法在刷入前备份")
        }

        // 3. A/B slot 提示
        val slotSuffix = readSlotSuffix()
        if (slotSuffix.isNotEmpty()) {
            warnings.add("A/B 设备，当前活动 slot：$slotSuffix")
        }

        // 4. 高级模式提示
        if (advancedMode) {
            warnings.add("⚠️ 高级模式已开启")
        }

        PreflightResult(errors.isEmpty(), errors, warnings)
    }

    /**
     * 🚨 分区白名单 / 黑名单校验（每个任务 detect 后调）
     */
    fun validatePartition(partition: String): PartitionValidation {
        val p = partition.lowercase().trim()
        if (p.isBlank() || p == "unknown" || p == "error") {
            return PartitionValidation(false, "分区名无效：$partition")
        }
        if (p in FORBIDDEN_PARTITIONS) {
            return PartitionValidation(
                false,
                "分区 $p 在禁刷黑名单（基带/校准/系统数据/Bootloader）"
            )
        }
        if (p !in ALLOWED_PARTITIONS) {
            return PartitionValidation(
                false,
                "分区 $p 不在白名单（仅允许 ${ALLOWED_PARTITIONS.joinToString()}）"
            )
        }
        return PartitionValidation(true, "允许刷入 $p")
    }

    /**
     * 🚨 文件大小校验（刷入前调）
     */
    fun validateFileSize(filePath: String, partition: String): Boolean {
        val p = partition.lowercase().trim()
        val limit = SIZE_LIMITS[p] ?: return false
        
        // 🚀 修复：用 su -c stat 算出文件大小，防止 Java File API 在安卓 11+ 读不到大小
        return try {
            val process = ProcessBuilder("su", "-c", "stat -c %s \"$filePath\"").start()
            val sizeStr = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val size = sizeStr.toLongOrNull() ?: 0L
            size in 1..limit
        } catch (e: Exception) {
            false
        }
    }

    /** 允许备份但禁止刷入的分区（段 B 备份 / 段 C 恢复时用） */
    fun isBackupOnly(partition: String): Boolean {
        val p = partition.lowercase().trim()
        return p == "persist" || p == "persistbak" || p == "persist_bak"
    }

    private fun readBatteryPercent(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            -1
        }
    }

    private suspend fun readSlotSuffix(): String {
        val r = HuijuiShell.exec("su -c 'getprop ro.boot.slot_suffix'")
        return r.out.firstOrNull()?.trim() ?: ""
    }
}
