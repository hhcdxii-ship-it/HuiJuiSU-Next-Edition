package com.huijui.su.next.core.root

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    /**
     * 🚀 多镜像刷入调度（流式 + 完整防格机闭环）
     *
     * 相对上版新增：
     *   - onTranslated 参数：刷入阶段实时输出翻译后的进度
     *   - 刷入阶段从 exec 换成 execStreaming，用户能看到"正在写入…"
     *   - 准备/备份阶段保持非流式（这些阶段快，不需要实时）
     *
     * 四阶段：
     *   Phase 0: 环境预检
     *   Phase 1: 并行准备（复制 + detect + 白名单 + 大小 + GKI）
     *   Phase 2: 串行备份
     *   Phase 3: 按优先级串行刷入（**流式**）
     */
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
            return@withContext tasks.map {
                FlashResult(it.fileName, "blocked", false, log)
            }
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
            onTranslated(FlashLogTranslator.TranslatedLine.Result(
                false, "同批次有文件准备失败（$failNames），全部拒绝"
            ))
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
                onTranslated(FlashLogTranslator.TranslatedLine.Result(
                    false, "备份失败：${backup.message}"
                ))
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

        // ===== Phase 3: 按优先级串行刷入（流式）=====
        val sorted = backedUp.sortedBy { priorityOf(it.targetPartition) }
        val resultMap = mutableMapOf<String, FlashResult>()

        for ((idx, ok) in sorted.withIndex()) {
            val backup = ok.backup!!
            onTranslated(FlashLogTranslator.TranslatedLine.Text(
                "正在刷入 ${ok.targetPartition}（${idx + 1}/${sorted.size}）…"
            ))

            val flashRes = HuijuiShell.execStreaming(
                command = "huijui_boot flash '${ok.localFile.absolutePath}' '${ok.targetPartition}'",
                timeoutMs = 3 * 60_000L
            ) { line, _ ->
                // 逐行翻译（用户看到的是"正在写入…"，不是 dd 日志）
                val translated = FlashLogTranslator.translate(line) ?: return@execStreaming
                runCatching { onTranslated(translated) }
            }
            runCatching { ok.localFile.delete() }

            val warningBlock = if (ok.warning.isNotBlank()) "${ok.warning}\n" else ""

            if (flashRes.isSuccess) {
                onTranslated(FlashLogTranslator.TranslatedLine.Result(
                    true, "${ok.targetPartition} 刷入成功"
                ))
                resultMap[ok.original.fileName] = FlashResult(
                    ok.original.fileName, ok.targetPartition, true,
                    buildString {
                        append(warningBlock)
                        appendLine("✅ 刷入成功：${ok.targetPartition}")
                        appendLine(backup.message)
                    }
                )
            } else {
                onTranslated(FlashLogTranslator.TranslatedLine.Text(
                    "刷入失败，正在回滚 ${ok.targetPartition}…"
                ))
                val restored = PartitionBackupManager.restorePartition(
                    ok.targetPartition, backup.backupPath
                )
                val restoreMsg = if (restored) {
                    "已自动回滚，${ok.targetPartition} 已恢复到备份状态"
                } else {
                    "⚠️ 回滚失败！请手动恢复备份：${backup.backupPath}"
                }
                onTranslated(FlashLogTranslator.TranslatedLine.Result(
                    false, "${ok.targetPartition} 刷入失败，$restoreMsg"
                ))
                resultMap[ok.original.fileName] = FlashResult(
                    ok.original.fileName, ok.targetPartition, false,
                    buildString {
                        append(warningBlock)
                        appendLine("❌ 刷入失败：${ok.targetPartition}")
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

            val detectRes = HuijuiShell.exec("huijui_boot detect '${localFile.absolutePath}'")
            val imageType = detectRes.out.firstOrNull()?.trim() ?: "unknown"

            val targetPartition = when {
                imageType == "boot_or_init_boot" -> {
                    val hasInitBoot = HuijuiShell.exec(
                        "test -e /dev/block/by-name/init_boot && echo yes || echo no"
                    ).out.firstOrNull()?.contains("yes") == true
                    if (hasInitBoot) "init_boot" else "boot"
                }
                imageType == "vendor_boot" -> "vendor_boot"
                imageType == "dtbo" -> "dtbo"
                else -> {
                    localFile.delete()
                    return PrepareOutcome.Fail(
                        task, "unknown", "无法识别镜像格式（$imageType）"
                    )
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

    private fun priorityOf(partition: String): Int = when (partition) {
        "dtbo" -> 0
        "vendor_boot" -> 1
        "boot", "init_boot" -> 2
        else -> 99
    }
}
