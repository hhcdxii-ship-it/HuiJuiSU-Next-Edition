package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object HuijuiScriptRunner {

    /**
     * 注意：assets 里**没有** Hsud 这个文件，真正的守护进程是 exploit/huijuisud。
     * 旧代码直接 open("Hsud") 抛 FileNotFoundException，被外层 catch 包成
     * 「释放 assets 失败」，于是热接管卡片永远失败。
     * 这里从 exploit/huijuisud 取，并在工作目录里落成脚本期望的文件名 Hsud。
     */
    private const val ASSET_DAEMON = "exploit/huijuisud"
    private const val DAEMON_LOCAL_NAME = "Hsud"
    private const val ASSET_HOT_INSTALL = "huijui_hot_install.sh"

    suspend fun runHotInstall(context: Context): HuijuiShell.CommandResult =
        withContext(Dispatchers.IO) {
            val assetDir = File(context.cacheDir, "huijui_assets").apply {
                deleteRecursively(); mkdirs()
            }
            val workDir = File(context.cacheDir, "huijui_work").apply {
                deleteRecursively(); mkdirs()
            }

            val targetKoName = KernelVersionMatcher.getExpectedKoFileName()

            try {
                // 1. 释放基础工具与脚本
                copyAsset(context, ASSET_DAEMON, assetDir, DAEMON_LOCAL_NAME)
                copyAsset(context, ASSET_HOT_INSTALL, assetDir)
                
                // 🚀 2. 大兼容核心：动态匹配并释放内核模块
                if (targetKoName != null) {
                    try {
                        // 从 assets/lkm/ 目录提取对应的 ko 文件
                        context.assets.open("exploit/$targetKoName").use { input ->
                            File(assetDir, targetKoName).outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (e: Exception) {
                        return@withContext HuijuiShell.CommandResult(
                            false, emptyList(), listOf("当前机型缺少匹配的 LKM 模块: $targetKoName")
                        )
                    }
                } else {
                    return@withContext HuijuiShell.CommandResult(
                        false, emptyList(), listOf("无法识别当前设备内核版本，不支持该机型")
                    )
                }

            } catch (e: Exception) {
                return@withContext HuijuiShell.CommandResult(
                    false, emptyList(), listOf("释放 assets 失败: ${e.message}")
                )
            }

            val script = File(assetDir, ASSET_HOT_INSTALL)
            val daemon = File(assetDir, DAEMON_LOCAL_NAME)
            val koFile = File(assetDir, targetKoName)

            HuijuiShell.exec("chmod 755 '${script.absolutePath}' '${daemon.absolutePath}'")
            
            // 将 target_ko 路径作为第三个参数传给脚本
            HuijuiShell.exec("sh '${script.absolutePath}' '${workDir.absolutePath}' '${assetDir.absolutePath}' '${koFile.name}'")
        }

    private fun copyAsset(
        context: Context,
        assetPath: String,
        destDir: File,
        destName: String = assetPath.substringAfterLast('/')
    ) {
        context.assets.open(assetPath).use { input ->
            File(destDir, destName).outputStream().use { output -> input.copyTo(output) }
        }
    }
}
