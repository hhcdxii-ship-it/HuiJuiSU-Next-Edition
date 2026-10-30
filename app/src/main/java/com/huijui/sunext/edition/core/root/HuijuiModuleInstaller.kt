package com.huijui.sunext.edition.core.root

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 🚀 HUI 模块安装器（跨管理器版本）
 *
 * 核心设计原则：尊重每个管理器的原生安装机制
 *   - HUI.ko（Ultra / Next）：走我们的"自定义解压 + install.sh + /proc/hui_config"流程
 *   - KernelSU 系：直接调 `ksud module install <zip>`
 *   - Magisk 系：直接调 `magisk --install-module <zip>`
 *   - APatch 系：优先 `apd module install`，fallback 手动解压到 /data/adb/modules
 *
 * 为什么不统一走 HUI 流程：
 *   非 HUI 管理器有自己的模块数据库、selinux context、post-fs-data 加载机制，
 *   我们的"手动 cp"会被它们忽略，模块装了也不生效。
 */
object HuijuiModuleInstaller {

    suspend fun installModuleStreaming(
        context: Context,
        zipUri: Uri,
        onTranslated: (FlashLogTranslator.TranslatedLine) -> Unit,
        onVolumeRequest: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val manager = RootManagerDetector.detect()

        when (manager) {
            RootManager.HUIJUI,
            RootManager.HUIJUI_ULTRA ->
                installViaHui(context, zipUri, onTranslated, onVolumeRequest)

            RootManager.NONE -> {
                onTranslated(FlashLogTranslator.TranslatedLine.Result(false, "未检测到 Root，无法安装模块"))
                false
            }

            else ->
                installViaManager(context, zipUri, manager, onTranslated, onVolumeRequest)
        }
    }

    // ==========================================
    // HUI.ko 安装路径（保留原有"解压 + install.sh + 热重载"流程）
    // ==========================================
    private suspend fun installViaHui(
        context: Context,
        zipUri: Uri,
        onTranslated: (FlashLogTranslator.TranslatedLine) -> Unit,
        onVolumeRequest: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "mod_temp").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            // ===== 1. 解压 =====
            onTranslated(FlashLogTranslator.TranslatedLine.Text("正在解压模块…"))
            var totalBytes = 0L
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(tempDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                            onTranslated(FlashLogTranslator.TranslatedLine.Result(false, "模块包含非法路径"))
                            return@withContext false
                        }
                        if (!entry.isDirectory) {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output -> zis.copyTo(output) }
                            totalBytes += entry.size.coerceAtLeast(0)
                            if (totalBytes > 0 && totalBytes % (512 * 1024) < entry.size) {
                                onTranslated(FlashLogTranslator.TranslatedLine.Progress(
                                    "已解压 ${totalBytes / 1024} KB"
                                ))
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // ===== 2. 校验 module.prop =====
            val propFile = File(tempDir, "module.prop")
            if (!propFile.exists()) {
                onTranslated(FlashLogTranslator.TranslatedLine.Result(false, "缺少 module.prop，不是合法模块"))
                tempDir.deleteRecursively()
                return@withContext false
            }

            val moduleId = propFile.readLines()
                .find { it.startsWith("id=") }
                ?.substringAfter("=")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "unknown_mod_${System.currentTimeMillis()}"

            // ===== 3. 复制到私有目录 =====
            onTranslated(FlashLogTranslator.TranslatedLine.Text("正在部署模块文件…"))
            val targetDir = "/data/adb/huijuisu/modules/$moduleId"
            HuijuiShell.exec("mkdir -p /data/adb/huijuisu/modules")
            HuijuiShell.exec("rm -rf '$targetDir'")
            if (!HuijuiShell.exec("cp -r '${tempDir.absolutePath}' '$targetDir'").isSuccess) {
                onTranslated(FlashLogTranslator.TranslatedLine.Result(false, "模块文件复制失败"))
                tempDir.deleteRecursively()
                return@withContext false
            }
            HuijuiShell.exec("chmod -R 755 '$targetDir'")

            // ===== 4. 执行 install.sh =====
            val installScript = File(targetDir, "install.sh")
            if (!installScript.exists()) {
                onTranslated(FlashLogTranslator.TranslatedLine.Text("模块无安装脚本，跳过执行"))
            } else {
                onTranslated(FlashLogTranslator.TranslatedLine.Text("正在执行模块安装脚本…"))
                val scriptCmd = buildString {
                    append("cd '$targetDir' && ")
                    append("MODPATH='$targetDir' MODID='$moduleId' ")
                    append("sh '$targetDir/install.sh' 2>&1")
                }

                val stream = HuijuiShell.execStreaming(
                    command = scriptCmd,
                    timeoutMs = 5 * 60_000L
                ) { line, _ ->
                    val translated = FlashLogTranslator.translate(line) ?: return@execStreaming
                    when (translated) {
                        is FlashLogTranslator.TranslatedLine.VolumeRequest ->
                            runCatching { onVolumeRequest(translated.message) }
                        else ->
                            runCatching { onTranslated(translated) }
                    }
                }

                if (!stream.isSuccess) {
                    val errMsg = stream.err.lastOrNull()?.takeIf { it.isNotBlank() }
                        ?: "install.sh 执行失败"
                    onTranslated(FlashLogTranslator.TranslatedLine.Result(false, errMsg.take(120)))
                    tempDir.deleteRecursively()
                    return@withContext false
                }
            }

            // ===== 5. HUI 专属热重载 =====
            onTranslated(FlashLogTranslator.TranslatedLine.Text("正在通知 HUI 内核热重载…"))
            HuijuiShell.exec("echo 'RELOAD_MODULES' > /proc/hui_config")

            tempDir.deleteRecursively()
            onTranslated(FlashLogTranslator.TranslatedLine.Result(true, "模块安装完成"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onTranslated(FlashLogTranslator.TranslatedLine.Result(
                false, "安装异常：${e.message?.take(80)}"
            ))
            tempDir.deleteRecursively()
            false
        }
    }

    // ==========================================
    // 非 HUI 管理器安装路径（调官方命令）
    // ==========================================
    private suspend fun installViaManager(
        context: Context,
        zipUri: Uri,
        manager: RootManager,
        onTranslated: (FlashLogTranslator.TranslatedLine) -> Unit,
        onVolumeRequest: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "module_${System.currentTimeMillis()}.zip")
        try {
            // 1. 复制 zip 到临时文件（管理器的命令需要真实路径）
            onTranslated(FlashLogTranslator.TranslatedLine.Text("正在准备安装包…"))
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
            }
            val zipPath = tempZip.absolutePath

            // 2. 按管理器选择命令
            val (cmd, managerName) = when (manager) {
                RootManager.KERNELSU,
                RootManager.KERNELSU_NEXT,
                RootManager.SUKISU_ULTRA,
                RootManager.BAKASU,
                RootManager.YUKISU,
                RootManager.KOWSU,
                RootManager.RKSU,
                RootManager.MKSU,
                RootManager.WILDKSU ->
                    "ksud module install '$zipPath' 2>&1" to "KernelSU"

                RootManager.MAGISK,
                RootManager.MAGISK_WEAVEMASK,
                RootManager.MAGISK_KITSUNE,
                RootManager.MAGISK_ALPHA,
                RootManager.MAGISK_DELTA ->
                    "magisk --install-module '$zipPath' 2>&1" to "Magisk"

                RootManager.APATCH,
                RootManager.ASTER,
                RootManager.FOLKPATCH ->
                    // APatch 优先 apd，fallback 手动解压
                    ("(command -v apd >/dev/null && apd module install '$zipPath' 2>&1) || " +
                            "(mkdir -p /data/adb/modules && cd /data/adb/modules && " +
                            "unzip -o '$zipPath' 2>&1 && echo 'installed-manually')") to "APatch"

                else -> {
                    onTranslated(FlashLogTranslator.TranslatedLine.Result(
                        false, "不支持的管理器：${manager.displayName}"
                    ))
                    return@withContext false
                }
            }

            // 3. 流式执行
            onTranslated(FlashLogTranslator.TranslatedLine.Text("正在通过 $managerName 安装…"))
            val result = HuijuiShell.execStreaming(
                command = cmd,
                timeoutMs = 5 * 60_000L
            ) { line, _ ->
                val translated = FlashLogTranslator.translate(line) ?: return@execStreaming
                when (translated) {
                    is FlashLogTranslator.TranslatedLine.VolumeRequest ->
                        runCatching { onVolumeRequest(translated.message) }
                    else ->
                        runCatching { onTranslated(translated) }
                }
            }

            if (result.isSuccess) {
                onTranslated(FlashLogTranslator.TranslatedLine.Result(
                    true, "模块已通过 $managerName 安装，可能需要重启生效"
                ))
                true
            } else {
                val err = result.err.lastOrNull()?.takeIf { it.isNotBlank() }
                    ?: result.out.lastOrNull()?.takeIf { it.isNotBlank() }
                    ?: "$managerName 安装失败"
                onTranslated(FlashLogTranslator.TranslatedLine.Result(false, err.take(120)))
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onTranslated(FlashLogTranslator.TranslatedLine.Result(
                false, "安装异常：${e.message?.take(80)}"
            ))
            false
        } finally {
            runCatching { tempZip.delete() }
        }
    }

    // ==========================================
    // 音量键双保险（不变）
    // ==========================================
    suspend fun sendVolumeKey(confirm: Boolean): Boolean = withContext(Dispatchers.IO) {
        val keyCode = if (confirm) 24 else 25
        val evCode = if (confirm) 115 else 114

        HuijuiShell.exec("input keyevent $keyCode")

        val devicePath = findVolumeKeyDevice()
        if (devicePath != null) {
            HuijuiShell.exec("sendevent $devicePath 1 $evCode 1")
            HuijuiShell.exec("sendevent $devicePath 0 0 0")
            HuijuiShell.exec("sendevent $devicePath 1 $evCode 0")
            HuijuiShell.exec("sendevent $devicePath 0 0 0")
            true
        } else {
            false
        }
    }

    private suspend fun findVolumeKeyDevice(): String? {
        val result = HuijuiShell.exec("getevent -lp 2>/dev/null")
        if (!result.isSuccess) return null

        var currentDevice: String? = null
        for (line in result.out) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                currentDevice = trimmed.substringAfter(": ").trim()
            } else if (trimmed.contains("KEY_VOLUMEUP") ||
                trimmed.contains("KEY_VOLUMEDOWN")) {
                if (currentDevice != null) return currentDevice
            }
        }
        return null
    }

    // 旧 API 兼容
    suspend fun installModule(
        context: Context,
        zipUri: Uri,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        installModuleStreaming(
            context = context,
            zipUri = zipUri,
            onTranslated = { },
            onVolumeRequest = { }
        )
    }
}
