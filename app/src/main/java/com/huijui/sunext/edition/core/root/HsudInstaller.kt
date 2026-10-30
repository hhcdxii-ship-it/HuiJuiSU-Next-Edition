package com.huijui.sunext.edition.core.root

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HsudInstaller {

    suspend fun installLkm(
        context: Context,
        installToInactiveSlot: Boolean,
        localImgUri: Uri? = null,
        onLog: (String) -> Unit,
        customKoUri: Uri? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val isAndroid14Plus = Build.VERSION.SDK_INT >= 34
            val arch = getArchitecture()
            val targetPartitionBase = if (isAndroid14Plus) "init_boot" else "boot"
            
            onLog("> [引擎] 激活纯血 hsud 引擎，接管 $targetPartitionBase")
            onLog("> [环境] Android ${Build.VERSION.RELEASE} | 架构 $arch")

            val workDir = File(context.cacheDir, "huijui_patch")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()

            // 准备内核模块
            val cacheKo: File
            if (customKoUri != null) {
                onLog("> [模块] 正在读取用户提供的自定义内核模块...")
                cacheKo = File(workDir, "custom_huijui.ko")
                if (!copyUriToFile(context, customKoUri, cacheKo)) {
                    onLog("❌ 错误：无法读取自定义模块文件。")
                    return@withContext false
                }
            } else {
                val kernelVersion = getKernelVersion()
                val (mainVer, subVer) = parseKernelVersion(kernelVersion)
                val gkiBranch = matchGkiBranch(mainVer, subVer)
                val koName = "HuiJuiPhantom_${gkiBranch}_${arch}.ko"
                onLog("> [模块] 匹配内置纯血内核: $koName")
                cacheKo = File(workDir, koName)
                if (!extractAsset(context, "exploit/$koName", cacheKo)) {
                    onLog("❌ 错误：APK 中缺失 $koName，请尝试手动选择本地模块。")
                    return@withContext false
                }
            }

            // 提取守护进程
            val cacheSud = File(workDir, "huijuisud")
            if (!extractAsset(context, "exploit/huijuisud", cacheSud)) {
                onLog("❌ 错误：APK 中缺失 huijuisud 引擎。")
                return@withContext false
            }
            cacheSud.setExecutable(true)

            // 准备待注入文件
            onLog("> [修补] 正在准备注入文件...")
            val targetImgFile = File(workDir, "stock_boot.img")
            val slotSuffix = if (installToInactiveSlot) {
                if (getSlotSuffix() == "_a") "_b" else "_a"
            } else getSlotSuffix()
            val targetPartition = targetPartitionBase + slotSuffix

            if (localImgUri != null) {
                onLog("> [读取] 正在读取用户提供的本地镜像...")
                if (!copyUriToFile(context, localImgUri, targetImgFile)) {
                    onLog("❌ 错误：无法读取本地镜像文件。")
                    return@withContext false
                }
            } else {
                onLog("> [提取] 正在提取底层分区 $targetPartition ...")
                HuijuiShell.exec("su -c 'dd if=/dev/block/by-name/$targetPartition of=${targetImgFile.absolutePath}'")
                if (!targetImgFile.exists() || targetImgFile.length() == 0L) {
                    onLog("❌ 错误：提取分区失败，请检查 Root 权限。")
                    return@withContext false
                }
                // 备份原厂镜像
                val backupDir = File("/sdcard/Download/HuiJui_Backup")
                backupDir.mkdirs()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "原厂_${targetPartition}_$timestamp.img")
                targetImgFile.copyTo(backupFile, overwrite = true)
                onLog("> [防砖] 备份成功: ${backupFile.name}")
            }

            val hFiles = File(workDir, "huijui_files")
            hFiles.mkdirs()
            cacheKo.copyTo(File(hFiles, "huijui_phantom.ko"), overwrite = true)
            cacheSud.copyTo(File(hFiles, "huijuisud"), overwrite = true)

            val rcFile = File(hFiles, "huijui.rc")
            rcFile.writeText("on post-fs-data\n    insmod /sbin/huijui_phantom.ko\n    exec - root -- /sbin/huijuisud\n")

            // 🚀 JNI 执行核心修补
            onLog("> [修补] 开始解包镜像...")
            if (HsudNative.runCommand(workDir.absolutePath, "hsud", "unpack", "stock_boot.img") != 0) {
                onLog("❌ 解包失败"); return@withContext false
            }

            onLog("> [修补] 正在向 ramdisk 注入...")
            HsudNative.runCommand(workDir.absolutePath, "hsud", "cpio", "ramdisk.cpio", "mkdir 0755 sbin")
            HsudNative.runCommand(workDir.absolutePath, "hsud", "cpio", "ramdisk.cpio", "add 0644 sbin/huijui_phantom.ko huijui_files/huijui_phantom.ko")
            HsudNative.runCommand(workDir.absolutePath, "hsud", "cpio", "ramdisk.cpio", "add 0755 sbin/huijuisud huijui_files/huijuisud")

            if (isAndroid14Plus) {
                // 🚀 直接修改原生 init.rc，确保 .ko 第一时间被 insmod
                HsudNative.runCommand(workDir.absolutePath, "hsud", "cpio", "ramdisk.cpio", "extract init.rc init.rc.orig")
                val initRcFile = File(workDir, "init.rc.orig")
                if (initRcFile.exists()) {
                    initRcFile.appendText("\non early-init\n    insmod /sbin/huijui_phantom.ko\n    chmod 0755 /sbin/huijuisud\n")
                    HsudNative.runCommand(workDir.absolutePath, "hsud", "cpio", "ramdisk.cpio", "add 0750 init.rc init.rc.orig")
                }
            }

            onLog("> [修补] 重新打包镜像...")
            if (HsudNative.runCommand(workDir.absolutePath, "hsud", "repack", "stock_boot.img", "new_boot.img") != 0) {
                onLog("❌ 打包失败"); return@withContext false
            }

            val newBoot = File(workDir, "new_boot.img")
            if (!newBoot.exists() || newBoot.length() == 0L) {
                onLog("❌ [失败] repack 未生成有效镜像")
                return@withContext false
            }

            if (localImgUri != null) {
                val outFile = File("/sdcard/Download/HuiJuiPhantom_patched_${targetPartition}.img")
                newBoot.copyTo(outFile, overwrite = true)
                onLog("✅ [成功] 本地镜像修补完成！输出：${outFile.absolutePath}")
            } else {
                onLog("> [写入] 正在写回分区 $targetPartition ...")
                HuijuiShell.exec("su -c 'dd if=${newBoot.absolutePath} of=/dev/block/by-name/$targetPartition'")
                onLog("✅ [成功] 纯血内核注入完成！请立即重启设备。")
            }
            return@withContext true

        } catch (e: Exception) {
            onLog("❌ 系统异常: ${e.message}")
            false
        }
    }

    // --- 辅助函数 ---
    private fun getKernelVersion(): String = try { 
        Runtime.getRuntime().exec("uname -r").inputStream.bufferedReader().readText().trim() 
    } catch (e: Exception) { "" }

    private fun parseKernelVersion(version: String): Pair<Int, Int> { 
        val match = Regex("""(\d+)\.(\d+)""").find(version)
        return Pair(match?.groupValues?.get(1)?.toIntOrNull() ?: 0, match?.groupValues?.get(2)?.toIntOrNull() ?: 0) 
    }

    private fun getArchitecture(): String { 
        val abis = Build.SUPPORTED_ABIS
        return if (abis.contains("arm64-v8a")) "arm64" else "x86_64" 
    }

    private fun getSlotSuffix(): String = try { 
        Runtime.getRuntime().exec("getprop ro.boot.slot_suffix").inputStream.bufferedReader().readText().trim() 
    } catch (e: Exception) { "" }

    private fun matchGkiBranch(main: Int, sub: Int): String = when { 
        main >= 6 && sub >= 18 -> "android17-6.18"
        main >= 6 && sub >= 12 -> "android16-6.12"
        main >= 6 && sub >= 6 -> "android15-6.6"
        main >= 6 && sub >= 1 -> "android14-6.1"
        main == 5 && sub >= 15 -> "android14-5.15"
        main == 5 && sub >= 10 -> "android12-5.10"
        else -> "android12-5.10" 
    }

    private fun extractAsset(context: Context, assetPath: String, destFile: File): Boolean = try { 
        context.assets.open(assetPath).use { input -> FileOutputStream(destFile).use { input.copyTo(it) } }
        true 
    } catch (e: Exception) { false }

    private fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean = try { 
        val input: InputStream? = context.contentResolver.openInputStream(uri)
        input?.use { FileOutputStream(destFile).use { out -> it.copyTo(out) } }
        true 
    } catch (e: Exception) { false }
}
