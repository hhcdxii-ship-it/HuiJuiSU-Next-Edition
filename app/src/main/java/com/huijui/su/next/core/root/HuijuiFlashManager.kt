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
    data class FlashResult(val fileName: String, val targetPartition: String, val success: Boolean, val log: String)

    // 核心刷机调度
    suspend fun flashMultipleImages(context: Context, tasks: List<FlashTask>): List<FlashResult> = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "flash_images").apply { mkdirs() }
        
        // 1. 并发执行
        val results = tasks.map { task ->
            async {
                try {
                    // 复制到缓存
                    val localFile = File(cacheDir, task.fileName)
                    context.contentResolver.openInputStream(task.uri)?.use { input ->
                        FileOutputStream(localFile).use { output -> input.copyTo(output) }
                    }

                    // 2. 嗅探分区类型
                    val detectRes = HuijuiShell.exec("huijui_boot detect '${localFile.absolutePath}'")
                    var imageType = detectRes.out.firstOrNull()?.trim() ?: "unknown"

                    // 3. 智能分配目标分区
                    val targetPartition = when {
                        imageType == "boot_or_init_boot" -> {
                            // 判断是否有 init_boot 分区
                            val hasInitBoot = HuijuiShell.exec("test -e /dev/block/by-name/init_boot && echo yes || echo no").out.firstOrNull()?.contains("yes") == true
                            if (hasInitBoot) "init_boot" else "boot"
                        }
                        imageType == "vendor_boot" -> "vendor_boot"
                        imageType == "dtbo" -> "dtbo"
                        else -> return@async FlashResult(task.fileName, "unknown", false, "无法识别镜像格式")
                    }

                    // 4. 执行刷入
                    val flashRes = HuijuiShell.exec("huijui_boot flash '${localFile.absolutePath}' '$targetPartition'")
                    
                    localFile.delete() // 清理
                    FlashResult(task.fileName, targetPartition, flashRes.isSuccess, flashRes.out.joinToString("\n"))

                } catch (e: Exception) {
                    FlashResult(task.fileName, "error", false, e.message ?: "Unknown Error")
                }
            }
        }.awaitAll()

        results
    }
}
