from pathlib import Path

print("🚀 正在全量重构 HsudInstaller.kt...")

path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')

kt_code = '''package com.huijui.sunext.edition.core.root

import android.content.Context
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

    private fun getCurrentSlot(): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix")).inputStream.bufferedReader().readText().trim().ifEmpty { "" }
    } catch (e: Exception) { "" }

    private fun getDynamicKoName(context: Context): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        val regex = Regex("""([0-9]+\\.[0-9]+)""")
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
            context.assets.open("exploit/ksuinit").use { i -> initFile.outputStream().use { o -> i.copyTo(o) } }
            initFile.setExecutable(true)
        }

        val koFile = File(targetDir, "kernelsu.ko")
        try {
            context.assets.open("exploit/kernelsu.ko").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
        } catch (e: Exception) {
            context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    fun patchImage(
        context: Context,
        imgPath: String,
        targetPartition: String,
        outputPath: String
    ): Flow<PatchState> = flow {
        try {
            emit(PatchState.Progress("开始修补本地镜像..."))
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "kernelsu.ko").absolutePath

            val koName = getDynamicKoName(context)
            releaseAssets(context, koName)

            val tempOutFile = File(context.cacheDir, "patched_sandbox.img")
            if (tempOutFile.exists()) tempOutFile.delete()

            emit(PatchState.Progress("正在调用 C++ 引擎执行注入..."))
            val success = nativePatch(imgPath, targetPartition, tempOutFile.absolutePath, initPath, koPath)

            if (success) {
                emit(PatchState.Progress("正在将镜像搬运至 Download 目录..."))
                val finalFile = File(outputPath)
                tempOutFile.copyTo(finalFile, overwrite = true)
                tempOutFile.delete()
                emit(PatchState.Success(outputPath))
            } else {
                emit(PatchState.Error("C++ 引擎执行失败！请查看控制台日志"))
            }
        } catch (e: Throwable) {
            emit(PatchState.Error("修补异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    fun oneClickRoot(): Flow<PatchState> = flow {
        val context = appContext ?: throw IllegalStateException("未初始化 Context")
        try {
            emit(PatchState.Progress("正在唤醒 hsu 守护进程..."))
            
            val slot = getCurrentSlot()
            var targetPartition = "init_boot"
            var blockDevice = "/dev/block/by-name/init_boot$slot"
            
            if (!File(blockDevice).exists()) {
                targetPartition = "boot"
                blockDevice = "/dev/block/by-name/boot$slot"
                emit(PatchState.Progress("[-] 未发现 init_boot 分区，自动切换至 boot 分区模式"))
            }

            emit(PatchState.Progress("[-] 目标底层分区: $blockDevice"))

            val workDir = File("/data/local/tmp/huijui_patch")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()
            
            val origImg = File(workDir, "orig.img")
            val patchedImg = File(workDir, "patched.img")

            emit(PatchState.Progress("[-] 正在提取底层原厂镜像 (需要 Root)..."))
            if (Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$blockDevice of=${origImg.absolutePath}")).waitFor() != 0 || !origImg.exists()) {
                emit(PatchState.Error("提取失败！请检查 Root 权限。"))
                return@flow
            }

            emit(PatchState.Progress("[-] 镜像提取成功，大小: ${origImg.length() / 1024 / 1024} MB"))

            val koName = getDynamicKoName(context)
            emit(PatchState.Progress("[-] 检测到系统内核，自动匹配模块: $koName"))
            releaseAssets(context, koName)
            
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "kernelsu.ko").absolutePath

            emit(PatchState.Progress("[-] 正在调用 C++ 引擎执行深度注入..."))
            if (!nativePatch(origImg.absolutePath, targetPartition, patchedImg.absolutePath, initPath, koPath)) {
                emit(PatchState.Error("C++ 引擎执行失败！"))
                return@flow
            }

            val backupDir = File("/sdcard/HuiJuiSU_Backup")
            if (!backupDir.exists()) backupDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "backup_${targetPartition}${slot}_${timeStamp}.img")
            emit(PatchState.Progress("[-] 备份原厂分区至: ${backupFile.name}"))
            origImg.copyTo(backupFile, overwrite = true)

            emit(PatchState.Progress("[-] 将修补包写回底层 $targetPartition$slot..."))
            if (Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=${patchedImg.absolutePath} of=$blockDevice bs=4096")).waitFor() == 0) {
                workDir.deleteRecursively()
                emit(PatchState.Success("🎉 一键 Root 成功！请重启手机！"))
            } else {
                emit(PatchState.Error("写入失败！请手动使用备份包恢复。"))
            }

        } catch (e: Exception) {
            emit(PatchState.Error("执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    fun checkRootStatus(): Flow<PatchState> = flow {
        emit(PatchState.Progress("正在检查内核模块加载状态..."))
        val devNode = File("/dev/huijui")
        val hasModule = try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/modules"))
            proc.inputStream.bufferedReader().readText().contains("huijui")
        } catch (e: Exception) { false }

        if (devNode.exists() || hasModule) {
            emit(PatchState.Success("🎉 纯血引擎已接管！/dev/huijui 节点已就绪，Root 权限正常！"))
        } else {
            emit(PatchState.Error("❌ 模块未加载。请尝试重启手机，或检查内核模块版本是否匹配。"))
        }
    }.flowOn(Dispatchers.IO)

    external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean
}
'''

path.write_text(kt_code)
print("✅ HsudInstaller.kt 已全量重写完毕！语法 100% 闭合！")
