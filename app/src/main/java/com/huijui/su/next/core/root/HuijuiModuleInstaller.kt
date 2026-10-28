package com.huijui.su.next.core.root

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

object HuijuiModuleInstaller {

    // 安全解压并安装
    suspend fun installModule(context: Context, zipUri: Uri, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "mod_temp").apply { deleteRecursively(); mkdirs() }
        var extractedSize = 0L

        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(tempDir, entry.name)
                        // 防 ZipSlip 漏洞
                        if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                            return@withContext false 
                        }
                        if (!entry.isDirectory) {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output -> zis.copyTo(output) }
                            extractedSize += entry.size
                            onProgress((extractedSize / 1024).toInt()) // 假进度计算
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 检查是否有 module.prop (标准模块特征)
            val propFile = File(tempDir, "module.prop")
            if (!propFile.exists()) return@withContext false

            // 读取包名/ID
            val moduleId = propFile.readLines().find { it.startsWith("id=") }?.substringAfter("=")?.trim() ?: "unknown_mod"
            val targetDir = File("/data/adb/huijuisu/modules/$moduleId")
            
            // 将临时文件移动到正式目录（需要 Root 权限）
            HuijuiShell.exec("rm -rf ${targetDir.absolutePath}")
            HuijuiShell.exec("mkdir -p /data/adb/huijuisu/modules")
            HuijuiShell.exec("cp -r ${tempDir.absolutePath} ${targetDir.absolutePath}")
            HuijuiShell.exec("chmod -R 755 ${targetDir.absolutePath}")

            // 🚀 核心：通知内核热重载挂载！
            HuijuiShell.exec("echo 'RELOAD_MODULES' > /proc/hui_config")
            
            tempDir.deleteRecursively()
            return@withContext true

        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return@withContext false
        }
    }
}
