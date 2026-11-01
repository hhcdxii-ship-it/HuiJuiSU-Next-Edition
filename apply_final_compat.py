import os
import re
from pathlib import Path

print("🚀 开始注入 终极兼容与全自动一键 Root 引擎...")

# ==========================================
# 1. 全量重写 HsudInstaller.kt (极限兼容版)
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

    /**
     * 🔥 极限兼容：双重探测 A/B 槽位
     */
    private fun getCurrentSlot(): String {
        try {
            // 1. 优先用 getprop
            val proc = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix"))
            val slot = proc.inputStream.bufferedReader().readText().trim()
            if (slot.isNotEmpty()) return slot
            
            // 2. 兜底读 /proc/cmdline
            val cmdline = File("/proc/cmdline").readText()
            val match = Regex("androidboot.slot_suffix=([_a-zA-Z0-9]+)").find(cmdline)
            if (match != null) return match.groupValues[1]
        } catch (e: Exception) {}
        return "" // 部分老旧机器没有槽位
    }

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
        // 每次强制刷新 ko 防止版本残留
        context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
    }

    /**
     * 🚀 终极兼容一键提取修补并刷入
     */
    fun oneClickRoot(): Flow<PatchState> = flow {
        val context = appContext ?: throw IllegalStateException("未初始化 Context")
        try {
            val slot = getCurrentSlot()
            var targetPartition = "init_boot"
            var blockDevice = ""
            
            // 🔥 极限探测：依次尝试 init_boot_a, init_boot, boot_a, boot
            val possiblePartitions = listOf(
                "init_boot$slot", "init_boot", 
                "boot$slot", "boot"
            )

            for (part in possiblePartitions) {
                val path = "/dev/block/by-name/$part"
                if (File(path).exists()) {
                    blockDevice = path
                    targetPartition = if (part.startsWith("init_boot")) "init_boot" else "boot"
                    break
                }
            }

            if (blockDevice.isEmpty()) {
                emit(PatchState.Error("❌ 致命错误：无法找到 boot 或 init_boot 分区节点！"))
                return@flow
            }

            emit(PatchState.Progress("[-] 目标底层分区: $blockDevice (类型: $targetPartition)"))

            val workDir = File("/data/local/tmp/huijui_patch")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()
            
            val origImg = File(workDir, "orig.img")
            val patchedImg = File(workDir, "patched.img")

            // 提取
            emit(PatchState.Progress("[-] 正在提取底层原厂镜像 (需要 Root)..."))
            if (Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$blockDevice of=${origImg.absolutePath}")).waitFor() != 0 || !origImg.exists()) {
                emit(PatchState.Error("提取失败！请检查 Root 权限。"))
                return@flow
            }
            
            val sizeMB = origImg.length() / (1024 * 1024)
            emit(PatchState.Progress("[-] 镜像提取成功，实际大小: ${sizeMB}MB"))
            if (targetPartition == "init_boot" && sizeMB > 20) {
                emit(PatchState.Error("分区异常：init_boot 提取大小大于 20MB，为防变砖已中止！"))
                return@flow
            }

            // 模块识别与释放
            val koName = getDynamicKoName()
            emit(PatchState.Progress("[-] 检测到系统内核，自动匹配模块: $koName"))
            releaseAssets(context, koName)
            
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "huijui_phantom.ko").absolutePath

            // C++ 引擎注入
            emit(PatchState.Progress("[-] 正在调用 C++ 引擎执行深度注入..."))
            if (!nativePatch(origImg.absolutePath, targetPartition, patchedImg.absolutePath, initPath, koPath)) {
                emit(PatchState.Error("C++ 引擎执行失败！"))
                return@flow
            }

            // 自动备份
            val backupDir = File("/sdcard/HuiJuiSU_Backup")
            if (!backupDir.exists()) backupDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "backup_${targetPartition}${slot}_${timeStamp}.img")
            emit(PatchState.Progress("[-] 备份原厂分区至: ${backupFile.name}"))
            origImg.copyTo(backupFile, overwrite = true)

            // 写回底层
            emit(PatchState.Progress("[-] 将修补包写回底层 $targetPartition..."))
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
print("✅ HsudInstaller.kt 已重写：超强分区兼容 + 智能模块匹配！")


# ==========================================
# 2. 修复并全量重写 UI 层 (不再需要你手动改)
# ==========================================
ui_path = 'app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt'
ui_file = Path(ui_path)

if ui_file.exists():
    content = ui_file.read_text()
    
    # 寻找 onClick = { 开始的位置，直接把它后面的所有内容全替换成干净的一键 Root
    start_idx = content.find('onClick = {')
    if start_idx != -1:
        # 保留前面的代码
        content = content[:start_idx]
        
        # 注入终极 UI 交互
        new_ui_logic = '''onClick = {
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
                        android.widget.Toast.makeText(context, "失败: ${state.errorMsg}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
) {
    Text(text = if (isProcessing) "正在全自动提取并注入..." else "🚀 一键智能 Root")
}
}
}
'''
        content += new_ui_logic
        ui_file.write_text(content)
        print("✅ CoreOverrideScreen.kt 已全量覆写：不需要你手动改任何一个字！")

print("🎉 全链路注入完成！直接执行 ./gradlew assembleDebug 即可起飞！")
