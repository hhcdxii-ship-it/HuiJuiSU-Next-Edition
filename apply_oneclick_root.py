import os
import re
from pathlib import Path

print("🚀 正在注入 V6 终极全自动一键 Root 引擎...")

# ==========================================
# 1. 全量重写 HsudInstaller.kt (全自动智能提取+识别+注入+刷入)
# ==========================================
kt_path = 'app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt'
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

    private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }

    private fun getCurrentSlot(): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix")).inputStream.bufferedReader().readText().trim().ifEmpty { "_a" }
    } catch (e: Exception) { "_a" }

    /**
     * 🔥 核心：精准识别 8 个内核模块
     */
    private fun getDynamicKoName(): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }
        val sdkInt = android.os.Build.VERSION.SDK_INT

        return when {
            sdkInt >= 37 || kernelVersion.contains("6.18") -> "HuiJuiPhantom_17_6_18_arm64.ko"
            sdkInt >= 36 || kernelVersion.contains("6.12") -> "HuiJuiPhantom_16_6_12_arm64.ko"
            sdkInt >= 35 || kernelVersion.contains("6.6")  -> "HuiJuiPhantom_15_6_6_arm64.ko"
            sdkInt == 34 -> if (kernelVersion.contains("5.15")) "HuiJuiPhantom_14_5_15_arm64.ko" else "HuiJuiPhantom_14_6_1_arm64.ko"
            sdkInt == 33 -> if (kernelVersion.contains("5.15")) "HuiJuiPhantom_13_5_15_arm64.ko" else "HuiJuiPhantom_13_5_10_arm64.ko"
            else -> if (kernelVersion.contains("5.10")) "HuiJuiPhantom_12_5_10_arm64.ko" else "HuiJuiPhantom_16_6_12_arm64.ko"
        }
    }

    private fun releaseAssets(context: Context, targetKoName: String) {
        val targetDir = File(context.filesDir, "patch_env")
        if (!targetDir.exists()) targetDir.mkdirs()
        val initFile = File(targetDir, "huijuiinit")
        val koFile = File(targetDir, "huijui_phantom.ko")
        
        if (!initFile.exists() || initFile.length() < 1000) {
            context.assets.open("exploit/huijuiinit").use { i -> initFile.outputStream().use { o -> i.copyTo(o) } }
            initFile.setExecutable(true)
        }
        
        // 每次强制更新 .ko 防止版本残留
        context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
    }

    /**
     * 🚀 终极一键提取修补并刷入
     */
    fun oneClickRoot(): Flow<PatchState> = flow {
        val context = appContext ?: throw IllegalStateException("未初始化 Context")
        try {
            val slot = getCurrentSlot()
            
            // 1. 智能判断设备使用的是 init_boot 还是 boot (GKI 设备有 init_boot，老设备只有 boot)
            // 通过尝试读取节点是否存在来判断，或者用文件大小
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

            // 2. 提取底层原厂镜像
            emit(PatchState.Progress("[-] 正在提取底层原厂镜像 (需要 Root)..."))
            if (Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$blockDevice of=${origImg.absolutePath}")).waitFor() != 0 || !origImg.exists()) {
                emit(PatchState.Error("提取失败！请检查 Root 权限。"))
                return@flow
            }
            emit(PatchState.Progress("[-] 镜像提取成功，大小: ${origImg.length() / 1024 / 1024} MB"))

            // 3. 动态匹配内核模块并释放
            val koName = getDynamicKoName()
            emit(PatchState.Progress("[-] 检测到系统内核，自动匹配模块: $koName"))
            releaseAssets(context, koName)
            
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "huijui_phantom.ko").absolutePath

            // 4. 调用底层 C++ 引擎
            emit(PatchState.Progress("[-] 正在调用 C++ 引擎执行注入..."))
            if (!nativePatch(origImg.absolutePath, targetPartition, patchedImg.absolutePath, initPath, koPath)) {
                emit(PatchState.Error("C++ 引擎执行失败！"))
                return@flow
            }

            // 5. 自动备份
            val backupDir = File("/sdcard/HuiJuiSU_Backup")
            if (!backupDir.exists()) backupDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "backup_${targetPartition}${slot}_${timeStamp}.img")
            emit(PatchState.Progress("[-] 备份原厂分区至: ${backupFile.name}"))
            origImg.copyTo(backupFile, overwrite = true)

            // 6. 刷回底层
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

    external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean
}
'''
Path(kt_path).write_text(kt_code)
print("✅ HsudInstaller.kt 已全量重写：智能识别内核、自动判断 init_boot/boot、全闭环刷入！")


# ==========================================
# 2. 修复 UI 层调用
# ==========================================
ui_path = 'app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt'
ui_file = Path(ui_path)
if ui_file.exists():
    content = ui_file.read_text()
    
    # 把 onClick 里的逻辑全量替换为调用 oneClickRoot()
    start_idx = content.find('onClick = {')
    if start_idx != -1:
        # 找到 onClick 对应的结尾
        end_idx = content.find('}', start_idx)
        # 粗暴一点，直接把 onClick 后的逻辑全部用正则换成新的
        # 这里为了不破坏大括号，用老方法：
        pass

    # 因为无法完美用正则改 Compose，我们直接建议用户手动替换：
    manual_code = '''
onClick = {
    isProcessing = true
    scope.launch {
        HsudInstaller.oneClickRoot().collect { state ->
            when (state) {
                is HsudInstaller.PatchState.Progress -> {
                    android.util.Log.d("HuiJuiSU", state.message)
                }
                is HsudInstaller.PatchState.Success -> {
                    isPatched = true
                    isProcessing = false
                    android.widget.Toast.makeText(context, "Root 成功，请重启！", android.widget.Toast.LENGTH_LONG).show()
                }
                is HsudInstaller.PatchState.Error -> {
                    android.util.Log.e("HuiJuiSU", state.errorMsg)
                    isProcessing = false
                }
            }
        }
    }
}
'''
    print("⚠️ UI 层因为 Compose 结构复杂，请手动打开 CoreOverrideScreen.kt，把 onClick = { ... } 里面的代码替换为：")
    print(manual_code)

print("🎉 注入完毕！请按照上方提示手动替换 UI 层 onClick 逻辑，然后执行 ./gradlew assembleDebug")
