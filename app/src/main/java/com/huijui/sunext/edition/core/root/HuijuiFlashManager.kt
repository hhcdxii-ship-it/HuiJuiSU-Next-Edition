package com.huijui.sunext.edition.core.root

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * 多镜像刷入调度。
 *
 * 🔴 重构前「刷入镜像」是**必然失败**的，原因是外部依赖根本不存在：
 *
 *   旧代码两步都调 `huijui_boot`：
 *     HuijuiShell.exec("huijui_boot detect '<img>'")
 *     HuijuiShell.execStreaming("huijui_boot flash '<img>' '<part>'")
 *
 *   但整个工程里没有任何地方产出这个二进制 —— `core/root/native/CMakeLists.txt`
 *   只 build 了 `hsud / huijuisud / huijuiinit / hsud_cli`，
 *   `boot_tool/huijui_boot.c` 只是一份没接进构建的源码，assets 里也没有。
 *   于是每次刷入都是 `sh: huijui_boot: not found`，被包装成"刷入失败"。
 *
 * 现在两步都改用工程内已有的能力：
 *   识别 → 直接在 Kotlin 里读镜像头（ANDROID! magic + header_size 区分 vendor_boot）
 *   写入 → `InstallIo.writeImage()`，dd + conv=fsync + sync + 全长回读 MD5
 *
 * 四阶段结构、预检、备份、失败回滚全部保留。
 */
object HuijuiFlashManager {

    data class FlashTask(val uri: Uri, val fileName: String)
    data class FlashResult(
        val fileName: String,
        val targetPartition: String,
        val success: Boolean,
        val log: String
    )

    private sealed class PrepareOutcome {
        abstract val original: FlashTask
        abstract val targetPartition: String

        data class Ok(
            override val original: FlashTask,
            override val targetPartition: String,
            val localFile: File,
            val warning: String = "",
            val backup: PartitionBackupManager.BackupResult? = null
        ) : PrepareOutcome()

        data class Fail(
            override val original: FlashTask,
            override val targetPartition: String,
            val reason: String
        ) : PrepareOutcome()
    }

    suspend fun flashMultipleImages(
        context: Context,
        tasks: List<FlashTask>,
        advancedMode: Boolean = false,
        onTranslated: (FlashLogTranslator.TranslatedLine) -> Unit = {}
    ): List<FlashResult> = withContext(Dispatchers.IO) {
        if (tasks.isEmpty()) return@withContext emptyList()

        // ===== Phase 0: 环境预检 =====
        val preflight = FlashGuard.preflightEnvironment(context, advancedMode)
        if (!preflight.passed) {
            val log = buildString {
                append("🛡️ 预检未通过：\n")
                preflight.errors.forEach { append("· $it\n") }
            }
            onTranslated(FlashLogTranslator.TranslatedLine.Result(false, log))
            return@withContext tasks.map { FlashResult(it.fileName, "blocked", false, log) }
        }

        val cacheDir = File(context.cacheDir, "flash_images").apply { mkdirs() }

        // ===== Phase 1: 并行准备 =====
        onTranslated(FlashLogTranslator.TranslatedLine.Text("正在检查 ${tasks.size} 个镜像…"))
        val prepared = tasks.map { task ->
            async { prepareOne(context, task, cacheDir, advancedMode) }
        }.awaitAll()

        val oks = prepared.filterIsInstance<PrepareOutcome.Ok>()
        val fails = prepared.filterIsInstance<PrepareOutcome.Fail>()

        if (fails.isNotEmpty()) {
            oks.forEach { runCatching { it.localFile.delete() } }
            val failNames = fails.joinToString { it.original.fileName }
            onTranslated(
                FlashLogTranslator.TranslatedLine.Result(
                    false, "同批次有文件准备失败（$failNames），全部拒绝"
                )
            )
            return@withContext prepared.map {
                when (it) {
                    is PrepareOutcome.Ok -> FlashResult(
                        it.original.fileName, it.targetPartition, false,
                        "同批次有文件准备失败，全部拒绝"
                    )
                    is PrepareOutcome.Fail -> FlashResult(
                        it.original.fileName, it.targetPartition, false, it.reason
                    )
                }
            }
        }

        // ===== Phase 2: 串行备份 =====
        val backedUp = mutableListOf<PrepareOutcome.Ok>()
        for (ok in oks) {
            onTranslated(FlashLogTranslator.TranslatedLine.Text("正在备份 ${ok.targetPartition}…"))
            val backup = PartitionBackupManager.backupPartition(ok.targetPartition)
            if (!backup.success) {
                oks.forEach { runCatching { it.localFile.delete() } }
                onTranslated(
                    FlashLogTranslator.TranslatedLine.Result(false, "备份失败：${backup.message}")
                )
                return@withContext prepared.map {
                    when (it) {
                        is PrepareOutcome.Ok -> FlashResult(
                            it.original.fileName, it.targetPartition, false,
                            "🛡️ 备份失败：${backup.message}"
                        )
                        is PrepareOutcome.Fail -> FlashResult(
                            it.original.fileName, it.targetPartition, false, it.reason
                        )
                    }
                }
            }
            backedUp.add(ok.copy(backup = backup))
        }

        // ===== Phase 3: 按优先级串行刷入 =====
        val sorted = backedUp.sortedBy { priorityOf(it.targetPartition) }
        val resultMap = mutableMapOf<String, FlashResult>()

        for ((idx, ok) in sorted.withIndex()) {
            val backup = ok.backup!!
            onTranslated(
                FlashLogTranslator.TranslatedLine.Text(
                    "正在刷入 ${ok.targetPartition}（${idx + 1}/${sorted.size}）…"
                )
            )

            val device = resolveDevice(ok.targetPartition)
            if (device == null) {
                resultMap[ok.original.fileName] = FlashResult(
                    ok.original.fileName, ok.targetPartition, false,
                    "❌ 找不到分区设备 /dev/block/by-name/${ok.targetPartition}"
                )
                continue
            }

            val write = InstallIo.writeImage(ok.localFile.absolutePath, device)
            runCatching { ok.localFile.delete() }

            val warningBlock = if (ok.warning.isNotBlank()) "${ok.warning}\n" else ""

            if (write.ok) {
                onTranslated(
                    FlashLogTranslator.TranslatedLine.Result(true, "${ok.targetPartition} 刷入成功")
                )
                resultMap[ok.original.fileName] = FlashResult(
                    ok.original.fileName, ok.targetPartition, true,
                    buildString {
                        append(warningBlock)
                        appendLine("✅ 刷入成功：${ok.targetPartition}")
                        appendLine(write.output)
                        append(backup.message)
                    }
                )
            } else {
                onTranslated(
                    FlashLogTranslator.TranslatedLine.Text("刷入失败，正在回滚 ${ok.targetPartition}…")
                )
                val restored = PartitionBackupManager.restorePartition(
                    ok.targetPartition, backup.backupPath
                )
                val restoreMsg = if (restored) {
                    "已自动回滚，${ok.targetPartition} 已恢复到备份状态"
                } else {
                    "⚠️ 回滚失败！请手动恢复备份：${backup.backupPath}"
                }
                onTranslated(
                    FlashLogTranslator.TranslatedLine.Result(
                        false, "${ok.targetPartition} 刷入失败，$restoreMsg"
                    )
                )
                resultMap[ok.original.fileName] = FlashResult(
                    ok.original.fileName, ok.targetPartition, false,
                    buildString {
                        append(warningBlock)
                        appendLine("❌ 刷入失败：${ok.targetPartition}")
                        appendLine(write.output)
                        appendLine(backup.message)
                        append(restoreMsg)
                    }
                )
            }
        }

        tasks.map { task ->
            resultMap[task.fileName] ?: FlashResult(task.fileName, "unknown", false, "未处理")
        }
    }

    private suspend fun prepareOne(
        context: Context,
        task: FlashTask,
        cacheDir: File,
        advancedMode: Boolean
    ): PrepareOutcome {
        return try {
            val localFile = File(cacheDir, task.fileName)
            context.contentResolver.openInputStream(task.uri)?.use { input ->
                FileOutputStream(localFile).use { output -> input.copyTo(output) }
            }

            val imageType = BootImageInspector.classify(localFile)
            val targetPartition = when (imageType) {
                BootImageInspector.Kind.BOOT_FAMILY -> {
                    if (InstallIo.exists("/dev/block/by-name/init_boot${InstallIo.currentSlot()}")) {
                        "init_boot"
                    } else {
                        "boot"
                    }
                }
                BootImageInspector.Kind.VENDOR_BOOT -> "vendor_boot"
                BootImageInspector.Kind.DTBO -> "dtbo"
                BootImageInspector.Kind.UNKNOWN -> {
                    localFile.delete()
                    return PrepareOutcome.Fail(task, "unknown", "无法识别镜像格式（文件头不匹配 boot / vendor_boot / dtbo）")
                }
            }

            val validation = FlashGuard.validatePartition(targetPartition)
            if (!validation.allowed) {
                localFile.delete()
                return PrepareOutcome.Fail(task, targetPartition, "🛡️ 拒绝刷入：${validation.reason}")
            }

            if (!FlashGuard.validateFileSize(localFile.absolutePath, targetPartition)) {
                val sizeMb = localFile.length() / 1024 / 1024
                localFile.delete()
                return PrepareOutcome.Fail(
                    task, targetPartition,
                    "🛡️ 拒绝刷入：文件 ${sizeMb}MB 超出 $targetPartition 上限"
                )
            }

            var gkiWarning = ""
            if (targetPartition == "boot" || targetPartition == "init_boot") {
                val gki = GkiChecker.checkImage(localFile.absolutePath)
                if (gki.canCheck && !gki.matched) {
                    if (!advancedMode) {
                        localFile.delete()
                        return PrepareOutcome.Fail(
                            task, targetPartition,
                            "🛡️ ${gki.message}\n内核版本不匹配，请开启高级模式强制刷入"
                        )
                    }
                    gkiWarning = "⚠️ ${gki.message}（高级模式强制刷入）"
                }
            }

            PrepareOutcome.Ok(task, targetPartition, localFile, warning = gkiWarning)
        } catch (e: Exception) {
            PrepareOutcome.Fail(task, "error", "准备阶段异常：${e.message ?: "Unknown"}")
        }
    }

    /** 分区名 → 实际设备节点（自动补 A/B 槽位后缀）。 */
    private suspend fun resolveDevice(partition: String): String? {
        val slot = InstallIo.currentSlot()
        val candidates = if (slot.isBlank()) {
            listOf(partition)
        } else {
            listOf("$partition$slot", partition)
        }
        return InstallIo.pickPartition(candidates)
    }

    private fun priorityOf(partition: String): Int = when (partition) {
        "dtbo" -> 0
        "vendor_boot" -> 1
        "boot", "init_boot" -> 2
        else -> 99
    }

    /**
     * Android boot image 头识别。
     *
     * 旧实现把这件事外包给不存在的 `huijui_boot detect`。这里用标准 AOSP 头格式自己判：
     *   - dtbo: magic `0xD7B7AB1E`
     *   - boot / init_boot / vendor_boot: magic `"ANDROID!"`（8 字节）
     *     v3/v4 头里 header_size 明显不同：boot≈1580，vendor_boot≈2112
     */
    private object BootImageInspector {

        enum class Kind { BOOT_FAMILY, VENDOR_BOOT, DTBO, UNKNOWN }

        private const val DTBO_MAGIC = 0xD7B7AB1EL
        private val ANDROID_MAGIC = "ANDROID!".toByteArray(Charsets.US_ASCII)

        fun classify(file: File): Kind {
            if (!file.isFile || file.length() < 64) return Kind.UNKNOWN
            return runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    val head = ByteArray(8)
                    raf.readFully(head)
                    if (head.contentEquals(ANDROID_MAGIC)) {
                        return@use classifyAndroid(raf)
                    }
                    raf.seek(0)
                    val rawInt = raf.readInt()
                    val asLittle = rawInt.toLong() and 0xFFFFFFFFL
                    val asBig = Integer.reverseBytes(rawInt).toLong() and 0xFFFFFFFFL
                    if (asLittle == DTBO_MAGIC || asBig == DTBO_MAGIC) {
                        Kind.DTBO
                    } else {
                        Kind.UNKNOWN
                    }
                }
            }.getOrDefault(Kind.UNKNOWN)
        }

        private fun classifyAndroid(raf: RandomAccessFile): Kind {
            // header_version 在 offset 40（v0 起就有这个字段）
            raf.seek(40)
            val headerVersion = readLeInt(raf)
            if (headerVersion < 3) return Kind.BOOT_FAMILY
            // header_size 在 offset 20
            raf.seek(20)
            val headerSize = readLeInt(raf)
            return if (headerSize >= 2000) Kind.VENDOR_BOOT else Kind.BOOT_FAMILY
        }

        private fun readLeInt(raf: RandomAccessFile): Int {
            val b = ByteArray(4)
            raf.readFully(b)
            return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
        }
    }
}
