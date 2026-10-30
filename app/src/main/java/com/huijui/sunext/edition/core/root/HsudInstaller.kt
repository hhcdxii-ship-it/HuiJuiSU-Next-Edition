package com.huijui.sunext.edition.core.root

import android.net.Uri
import android.os.Build
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object HsudInstaller {

    suspend fun installLkm(
        context: Context,
        installToInactiveSlot: Boolean,
        localImgUri: Uri? = null, // 🚀 如果用户选了本地镜像，就传进来
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val kernelVer = Runtime.getRuntime().exec("uname -r").inputStream.bufferedReader().readText().trim()
            val androidVer = Build.VERSION.RELEASE.toIntOrNull() ?: 0
            val abis = Build.SUPPORTED_ABIS
            val arch = if (abis.contains("arm64-v8a")) "arm64" else "x86_64"

            val versionMatch = Regex("""(\d+)\.(\d+)""").find(kernelVer)
            val mainVer = versionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val subVer = versionMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

            if (mainVer < 5 || (mainVer == 5 && subVer < 10)) {
                onLog("❌ 非 GKI 老内核，拒绝操作。")
                return@withContext false
            }

            // 1. 判断是否是本地镜像模式
            val isLocalImg = localImgUri != null
            var targetPartition = if (androidVer >= 14) "init_boot" else "boot"
            val slotSuffix = Runtime.getRuntime().exec("getprop ro.boot.slot_suffix").inputStream.bufferedReader().readText().trim()
            val isAbDevice = slotSuffix.isNotEmpty()

            if (!isLocalImg) {
                if (isAbDevice) {
                    if (installToInactiveSlot) {
                        val inactiveSlot = if (slotSuffix == "_a") "_b" else "_a"
                        targetPartition += inactiveSlot
                        onLog("📱 A/B 设备：注入未使用槽位 $targetPartition")
                    } else {
                        targetPartition += slotSuffix
                        onLog("📱 A/B 设备：注入活动槽位 $targetPartition")
                    }
                } else {
                    onLog("📱 非 A/B 设备：注入分区 $targetPartition")
                }
            } else {
                onLog("📂 本地镜像模式：准备处理用户提供的镜像...")
            }

            // 2. 匹配内核模块
            val gkiBranch = when {
                mainVer >= 6 && subVer >= 18 -> "android17-6.18"
                mainVer >= 6 && subVer >= 12 -> "android16-6.12"
                mainVer >= 6 && subVer >= 6  -> "android15-6.6"
                mainVer >= 6 && subVer >= 1  -> "android14-6.1"
                mainVer == 5 && subVer >= 15 -> "android14-5.15"
                mainVer == 5 && subVer >= 10 -> "android12-5.10"
                else -> "android12-5.10"
            }
            val koName = "HuiJuiPhantom_${gkiBranch}_${arch}.ko"
            onLog("🎯 模块匹配: $koName")

            // 3. 释放工具与模块
            val workDir = File(context.cacheDir, "huijui_patch")
            workDir.mkdirs()
            val cacheKo = File(workDir, koName)
            context.assets.open("exploit/$koName").use { input -> FileOutputStream(cacheKo).use { input.copyTo(it) } }
            
            val cacheMagiskboot = File(workDir, "magiskboot")
            try {
                context.assets.open("exploit/magiskboot").use { input -> FileOutputStream(cacheMagiskboot).use { input.copyTo(it) } }
                Runtime.getRuntime().exec("chmod 755 ${cacheMagiskboot.absolutePath}").waitFor()
            } catch (e: Exception) {
                onLog("❌ 致命错误：assets 中没有 magiskboot！")
                return@withContext false
            }

            // 4. 构建 Shell 脚本逻辑
            onLog("> hsud 正在接管底层镜像修补...")
            val targetImgFile = File(workDir, "stock_boot.img")
            
            var scriptCmd = "cd ${workDir.absolutePath}\n"

            if (isLocalImg) {
                // 把用户选的文件拷贝到工作目录
                context.contentResolver.openInputStream(localImgUri!!).use { input ->
                    FileOutputStream(targetImgFile).use { output -> input?.copyTo(output) }
                }
                onLog("✅ 本地镜像已读取")
            } else {
                // 从分区提取
                scriptCmd += "dd if=/dev/block/by-name/$targetPartition of=stock_boot.img\n"
            }

            scriptCmd += """
                ./magiskboot unpack stock_boot.img
                
                # 注入模块
                mkdir -p ramdisk/overlay.d/sbin
                cp ${cacheKo.absolutePath} ramdisk/overlay.d/sbin/huijui_phantom.ko
                echo '#!/system/bin/sh' > ramdisk/overlay.d/sbin/huijui.rc
                echo 'insmod /sbin/huijui_phantom.ko' >> ramdisk/overlay.d/sbin/huijui.rc
                chmod 755 ramdisk/overlay.d/sbin/huijui.rc
                
                if [ -f ramdisk/init.rc ]; then
                    echo 'import /sbin/huijui.rc' >> ramdisk/init.rc
                fi
                
                ./magiskboot repack stock_boot.img new_boot.img
            """.trimIndent()

            if (isLocalImg) {
                // 本地模式：把修补好的镜像放到用户能访问的目录
                scriptCmd += "\ncp new_boot.img /sdcard/Download/HuiJuiPhantom_patched_${targetPartition}.img\n"
                scriptCmd += "echo 'PATCH_SUCCESS_LOCAL'\n"
            } else {
                // 自动模式：直接写回分区
                scriptCmd += "\ndd if=new_boot.img of=/dev/block/by-name/$targetPartition\n"
                scriptCmd += "echo 'PATCH_SUCCESS_LIVE'\n"
            }

            // 5. 执行
            val process = ProcessBuilder("su", "-c", scriptCmd).redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            onLog(result.trim())
            
            if (exitCode == 0 && (result.contains("PATCH_SUCCESS_LIVE") || result.contains("PATCH_SUCCESS_LOCAL"))) {
                if (isLocalImg) {
                    onLog("✅ 本地镜像修补完成！")
                    onLog("📁 已保存至 /sdcard/Download/HuiJuiPhantom_patched_${targetPartition}.img")
                    onLog("👉 请使用 fastboot flash ${targetPartition} 刷入该镜像。")
                } else {
                    if (installToInactiveSlot) {
                        onLog("✅ 未使用槽位修补完成！请勿立即重启，等待下次 OTA。")
                    } else {
                        onLog("✅ 活动分区修补完成！请立即重启。")
                    }
                }
                true
            } else {
                onLog("❌ 镜像修补失败，请检查日志。")
                false
            }
        } catch (e: Exception) {
            onLog("❌ 异常: ${e.message}")
            false
        }
    }
}
