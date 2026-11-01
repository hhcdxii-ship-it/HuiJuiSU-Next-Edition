package com.huijui.sunext.edition.core.root

import android.util.Log

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HsudInstaller {

    sealed class PatchState {
        data class Progress(val message: String) : PatchState()
        data class Success(val outputPath: String) : PatchState()
        data class Error(val errorMsg: String) : PatchState()
    }

    init {
        try {
            System.loadLibrary("hsud")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }
    
    // 兜底：通过反射获取全局 Application Context
    private fun getGlobalContext(): Context? {
        return appContext ?: try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThread.getMethod("currentApplication").invoke(null)
            currentApplication as? Context
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentSlot(): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix")).inputStream.bufferedReader().readText().trim().ifEmpty { "" }
    } catch (e: Exception) { "" }

    private fun getDynamicKoName(context: Context): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        val regex = Regex("""([0-9]+\.[0-9]+)""")
        val match = regex.find(kernelVersion)
        val kmi = match?.value ?: ""

        val allAssets = context.assets.list("exploit")?.filter { it.endsWith(".ko") } ?: emptyList()
        
        val fuzzyKmi = kmi.replace(".", "_")
        val exactMatch = allAssets.find { it.contains(kmi) || it.contains(fuzzyKmi) }
        if (exactMatch != null) return exactMatch

        return when {
            kmi.startsWith("6.18") -> allAssets.find { it.contains("17") } ?: "HuiJuiPhantom_17_6.18_arm64.ko"
            kmi.startsWith("6.12") -> allAssets.find { it.contains("16") } ?: "HuiJuiPhantom_android16-6.12_arm64.ko"
            kmi.startsWith("6.6")  -> allAssets.find { it.contains("15") } ?: "HuiJuiPhantom_15_6.6_arm64.ko"
            kmi.startsWith("6.1")  -> allAssets.find { it.contains("14") } ?: "HuiJuiPhantom_14_6.1_arm64.ko"
            kmi.startsWith("5.15") -> allAssets.find { it.contains("13") && it.contains("5.15") } ?: "HuiJuiPhantom_13_5.15_arm64.ko"
            kmi.startsWith("5.10") -> allAssets.find { it.contains("12") } ?: "HuiJuiPhantom_12_5.10_arm64.ko"
            else -> allAssets.firstOrNull() ?: "HuiJuiPhantom_android16-6.12_arm64.ko"
        }
    }

    private fun releaseAssets(context: Context, targetKoName: String) {
        val targetDir = File(context.filesDir, "patch_env")
        if (!targetDir.exists()) targetDir.mkdirs()
        
        val initFile = File(targetDir, "huijuiinit")
        if (!initFile.exists() || initFile.length() < 1000) {
            context.assets.open("exploit/huijuiinit").use { i -> initFile.outputStream().use { o -> i.copyTo(o) } }
            initFile.setExecutable(true)
        }

        val koFile = File(targetDir, "huijui_phantom.ko")
        context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
        
        // 释放 huijuisud 到同一目录
        val daemonFile = File(targetDir, "huijuisud")
        context.assets.open("exploit/huijuisud").use { i -> daemonFile.outputStream().use { o -> i.copyTo(o) } }
        daemonFile.setExecutable(true)
    }

    // 🚀 [重构剥离] 镜像修补逻辑已迁移至 ImagePatcher.kt




    fun patchImage(
        context: Context,
        imgPath: String,
        targetPartition: String,
        outputPath: String
    ): Flow<PatchState> = flow {
        // 🚀 直接交给完全独立的 ImagePatcher 模块处理，自身完全解耦
        ImagePatcher.patch(context, imgPath, outputPath).collect { state ->
            when (state) {
                is ImagePatcher.PatchState.Progress -> emit(PatchState.Progress(state.message))
                is ImagePatcher.PatchState.Success -> emit(PatchState.Success(state.outputPath))
                is ImagePatcher.PatchState.Error -> emit(PatchState.Error(state.errorMsg))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun oneClickRoot(): Flow<PatchState> = flow {
        val context = appContext ?: return@flow
        try {
            emit(PatchState.Progress("[-] 正在唤醒底层环境..."))
            
            // 1. 获取槽位
            val slotRes = HuijuiShell.exec("getprop ro.boot.slot_suffix")
            val slot = slotRes.out.firstOrNull()?.trim() ?: ""
            
            var targetPartition = "init_boot"
            var blockDevice = "/dev/block/by-name/init_boot" + slot
            
            val checkRes = HuijuiShell.exec("ls " + blockDevice)
            if (!checkRes.isSuccess) {
                targetPartition = "boot"
                blockDevice = "/dev/block/by-name/boot" + slot
                emit(PatchState.Progress("[-] 未发现 init_boot，自动切换至 boot 分区"))
            }

            val backupDir = File("/sdcard/Download")
            if (!backupDir.exists()) backupDir.mkdirs()
            val origImg = File(backupDir, "stock_" + targetPartition + ".img")
            val patchedImg = File(backupDir, "patched_" + targetPartition + ".img")

            emit(PatchState.Progress("[-] 正在提取原厂镜像..."))
            HuijuiShell.exec("dd if=" + blockDevice + " of=" + origImg.absolutePath + " bs=1M")

            if (!origImg.exists()) {
                emit(PatchState.Error("❌ 提取失败！"))
                return@flow
            }

            // 2. 准备修补工具
            emit(PatchState.Progress("[-] 正在准备修补环境..."))
            val workDir = File(context.cacheDir, "patch_work").apply { mkdirs() }
            val huijuiinit = File(workDir, "huijuiinit")
            val huijuisud = File(workDir, "huijuisud")
            val koFile = File(workDir, "huijui_phantom.ko")
            val hsudBin = File(workDir, "hsud_cli")

            context.assets.open("exploit/huijuiinit").use { it.copyTo(huijuiinit.outputStream()) }
            context.assets.open("exploit/huijuisud").use { it.copyTo(huijuisud.outputStream()) }
            val koName = getDynamicKoName(context)
            context.assets.open("exploit/" + koName).use { it.copyTo(koFile.outputStream()) }
            context.assets.open("exploit/hsud_cli").use { it.copyTo(hsudBin.outputStream()) }

            val workPath = "/data/local/tmp/huijui_work"
            HuijuiShell.exec("rm -rf " + workPath + " && mkdir -p " + workPath + " && chmod 777 " + workPath)

            listOf(hsudBin, huijuiinit, huijuisud, koFile, origImg).forEach {
                HuijuiShell.exec("cp " + it.absolutePath + " " + workPath + "/")
            }
            HuijuiShell.exec("chmod 755 " + workPath + "/hsud_cli " + workPath + "/huijuiinit " + workPath + "/huijuisud")

            // 3. 底层解包与打包
            emit(PatchState.Progress("[-] 正在注入纯血模块..."))
            HuijuiShell.exec("cd " + workPath + " && ./hsud_cli unpack stock_" + targetPartition + ".img")
            HuijuiShell.exec("cd " + workPath + " && ./hsud_cli cpio ramdisk.cpio mv init init.real add 0755 init huijuiinit add 0755 huijuisud huijuisud add 0755 force_insmod force_insmod add 0644 huijui_phantom.ko huijui_phantom.ko")
            HuijuiShell.exec("cd " + workPath + " && ./hsud_cli repack stock_" + targetPartition + ".img patched_" + targetPartition + ".img")

            HuijuiShell.exec("cp " + workPath + "/patched_" + targetPartition + ".img " + patchedImg.absolutePath)

            if (!patchedImg.exists()) {
                emit(PatchState.Error("❌ 引擎修补失败！"))
                return@flow
            }

            emit(PatchState.Progress("[-] 正在直接写回 " + blockDevice + "..."))
            HuijuiShell.exec("dd if=" + patchedImg.absolutePath + " of=" + blockDevice + " bs=4096")

            emit(PatchState.Success("✅ 一键提取并刷入成功！请重启手机生效！"))

        } catch (e: Throwable) {
            emit(PatchState.Error("❌ 执行异常: " + e.message))
        }
    }.flowOn(Dispatchers.IO)

    fun checkRootStatus(): Flow<PatchState> = flow {
        emit(PatchState.Progress("正在检查内核模块加载状态..."))
        val devNode = File("/dev/huijui")
        val hasModule = try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/modules"))
            proc.inputStream.bufferedReader().readText().contains("huijui")
        } catch (e: Exception) { false }

        if (devNode.exists() || hasModule) {
            emit(PatchState.Success("🎉 纯血引擎已接管！/dev/huijui 节点已就绪，Root 权限正常！"))
        } else {
            emit(PatchState.Error("❌ 模块未加载。请尝试重启手机，或检查内核模块版本是否匹配。"))
        }
    }.flowOn(Dispatchers.IO)

    external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean

    suspend fun installLkm(
        context: Context,
        installToInactiveSlot: Boolean,
        localImgUri: Uri?,
        onLog: (String) -> Unit,
        customKoUri: Uri? = null
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        onLog("[-] 正在准备底层镜像 (幽灵模式: $installToInactiveSlot)...")
        
        if (localImgUri == null) {
            onLog("❌ 未选择镜像文件！")
            return@withContext false
        }

        try {
            val tempFile = File(context.cacheDir, "lkm_temp.img")
            context.contentResolver.openInputStream(localImgUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                onLog("❌ 镜像读取失败，文件为空！")
                return@withContext false
            }

            val outPath = File(context.cacheDir, "patched_lkm.img").absolutePath
            var isSuccess = false

            // 直接调用完全独立的 ImagePatcher
            ImagePatcher.patch(context, tempFile.absolutePath, outPath).collect { state ->
                when (state) {
                    is ImagePatcher.PatchState.Progress -> onLog(state.message)
                    is ImagePatcher.PatchState.Success -> {
                        onLog("✅ LKM 修补成功: \${state.outputPath}")
                        isSuccess = true
                    }
                    is ImagePatcher.PatchState.Error -> {
                        onLog("❌ LKM 修补失败: \${state.errorMsg}")
                        isSuccess = false
                    }
                }
            }
            
            return@withContext isSuccess
        } catch (e: Throwable) {
            onLog("❌ 执行异常: \${e.message}")
            return@withContext false
        }
    }
}
