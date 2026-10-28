package com.huijui.su.next.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object HuijuiScriptRunner {

    private const val ASSET_DAEMON = "huijui_ksud"
    private const val ASSET_HOT_INSTALL = "huijui_hot_install.sh"

    suspend fun runHotInstall(context: Context): HuijuiShell.CommandResult =
        withContext(Dispatchers.IO) {
            val assetDir = File(context.cacheDir, "huijui_assets").apply {
                deleteRecursively(); mkdirs()
            }
            val workDir = File(context.cacheDir, "huijui_work").apply {
                deleteRecursively(); mkdirs()
            }

            try {
                copyAsset(context, ASSET_DAEMON, assetDir)
                copyAsset(context, ASSET_HOT_INSTALL, assetDir)
            } catch (e: Exception) {
                return@withContext HuijuiShell.CommandResult(
                    false, emptyList(), listOf("释放 assets 失败: ${e.message}")
                )
            }

            val script = File(assetDir, ASSET_HOT_INSTALL)
            val daemon = File(assetDir, ASSET_DAEMON)

            // 这里只传一个 String，不再传 timeouts
            HuijuiShell.exec("chmod 755 '${script.absolutePath}' '${daemon.absolutePath}'")
            HuijuiShell.exec("sh '${script.absolutePath}' '${workDir.absolutePath}' '${assetDir.absolutePath}'")
        }

    private fun copyAsset(context: Context, name: String, destDir: File) {
        context.assets.open(name).use { input ->
            File(destDir, name).outputStream().use { output -> input.copyTo(output) }
        }
    }
}
