import os
import re
from pathlib import Path

print("🚀 正在注入「工作中(HUI)」与「杂鱼语言」终极 UI 引擎...")

# ==========================================
# 1. 重写 HsudInstaller.kt，注入二次元文案 + 状态检测
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
        Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix")).inputStream.bufferedReader().readText().trim().ifEmpty { "" }
    } catch (e: Exception) { "" }

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
        context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
    }

    /**
     * 🚀 纯血傲娇一键 Root
     */
    fun oneClickRoot(): Flow<PatchState> = flow {
        val context = appContext ?: throw IllegalStateException("杂鱼，连 Context 都没初始化！")
        try {
            emit(PatchState.Progress("哼，本小姐才不想管你呢！正在唤醒 hsu 守护进程..."))
            
            val slot = getCurrentSlot()
            var targetPartition = "init_boot"
            var blockDevice = ""
            
            val possiblePartitions = listOf("init_boot$slot", "init_boot", "boot$slot", "boot")
            for (part in possiblePartitions) {
                val path = "/dev/block/by-name/$part"
                if (File(path).exists()) {
                    blockDevice = path
                    targetPartition = if (part.startsWith("init_boot")) "init_boot" else "boot"
                    break
                }
            }

            if (blockDevice.isEmpty()) {
                emit(PatchState.Error("杂鱼就是杂鱼，连底层分区节点都找不到！"))
                return@flow
            }

            emit(PatchState.Progress("[引擎] 激活纯血 hsu 引擎，接管 $targetPartition..."))
            emit(PatchState.Progress("[环境] 正在执行底层提取，别乱动哦！"))

            val workDir = File("/data/local/tmp/huijui_patch")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()
            
            val origImg = File(workDir, "orig.img")
            val patchedImg = File(workDir, "patched.img")

            if (Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$blockDevice of=${origImg.absolutePath}")).waitFor() != 0 || !origImg.exists()) {
                emit(PatchState.Error("提取失败！快检查有没有给本小姐 Root 权限！"))
                return@flow
            }
            
            val sizeMB = origImg.length() / (1024 * 1024)
            emit(PatchState.Progress("[读取] 镜像读取成功，大小: ${sizeMB}MB，准备塞入杂鱼内核..."))
            
            if (targetPartition == "init_boot" && sizeMB > 20) {
                emit(PatchState.Error("选错分区了吧？这玩意大于20MB，绝对要变砖，本小姐才不干呢！"))
                return@flow
            }

            val koName = getDynamicKoName()
            emit(PatchState.Progress("[模块] 匹配内置纯血内核: $koName"))
            releaseAssets(context, koName)
            
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "huijui_phantom.ko").absolutePath

            emit(PatchState.Progress("[修补] 正在向 ramdisk 注入 init 替换与 .ko 模块..."))
            if (!nativePatch(origImg.absolutePath, targetPartition, patchedImg.absolutePath, initPath, koPath)) {
                emit(PatchState.Error("底层 C++ 引擎罢工了！"))
                return@flow
            }

            val backupDir = File("/sdcard/HuiJuiSU_Backup")
            if (!backupDir.exists()) backupDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "backup_${targetPartition}${slot}_${timeStamp}.img")
            emit(PatchState.Progress("[策略] 正在备份原厂镜像，以防你这个杂鱼搞坏手机..."))
            origImg.copyTo(backupFile, overwrite = true)

            emit(PatchState.Progress("[修补] 重新打包镜像，正在写回底层，准备见证奇迹！"))
            if (Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=${patchedImg.absolutePath} of=$blockDevice bs=4096")).waitFor() == 0) {
                workDir.deleteRecursively()
                emit(PatchState.Success("✅ 工作中(HUI)完成！重启后本小姐给你 Root 权限！"))
            } else {
                emit(PatchState.Error("写入失败！请使用备份包救砖！"))
            }

        } catch (e: Exception) {
            emit(PatchState.Error("执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 🔍 纯血状态检测：检查是否真的拿到了 Root
     */
    fun checkRootStatus(): Flow<PatchState> = flow {
        emit(PatchState.Progress("杂鱼，让本小姐看看你到底有没有 Root 成功..."))
        
        val devNode = File("/dev/huijui")
        val hasModule = try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/modules"))
            proc.inputStream.bufferedReader().readText().contains("HuiJuiPhantom")
        } catch (e: Exception) { false }

        if (devNode.exists() || hasModule) {
            emit(PatchState.Success("🎉 恭喜你个杂鱼，纯血引擎已接管！/dev/huijui 节点已就绪，Root 权限已生效！"))
        } else {
            emit(PatchState.Error("❌ 还没有成功哦，可能还没重启，或者内核模块没加载上。"))
        }
    }.flowOn(Dispatchers.IO)

    external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean
}
'''
Path(kt_path).write_text(kt_code)
print("✅ HsudInstaller.kt 已重写：注入杂鱼语音 + 状态检测！")


# ==========================================
# 2. 暴力全量重写 UI 层 (彻底解决状态展示)
# ==========================================
ui_path = 'app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt'
ui_file = Path(ui_path)

if ui_file.exists():
    content = ui_file.read_text()
    
    # 确保有 logText 变量
    if 'var logText by remember' not in content:
        content = re.sub(r'(var isProcessing by remember \{ mutableStateOf\(false\) \})', r'\1\n    var logText by remember { mutableStateOf("杂鱼，点一下开始吧~") }', content)

    # 寻找 onClick 开始的位置，替换后面的逻辑
    start_idx = content.find('onClick = {')
    if start_idx != -1:
        content = content[:start_idx]
        
        new_ui_logic = '''onClick = {
        isProcessing = true
        logText = "工作中 (HUI)..."
        scope.launch {
            HsudInstaller.oneClickRoot().collect { state ->
                when (state) {
                    is HsudInstaller.PatchState.Progress -> {
                        logText = state.message
                    }
                    is HsudInstaller.PatchState.Success -> {
                        isProcessing = false
                        isPatched = true
                        logText = state.outputPath
                        android.widget.Toast.makeText(context, "处理成功，请重启", android.widget.Toast.LENGTH_LONG).show()
                    }
                    is HsudInstaller.PatchState.Error -> {
                        isProcessing = false
                        logText = state.errorMsg
                    }
                }
            }
        }
    }
) {
    Text(text = if (isProcessing) "工作中 (HUI)..." else "🚀 一键智能 Root")
}
// 故意保留几个大括号以兼容 Compose 结构
}
}
'''
        content += new_ui_logic
        ui_file.write_text(content)
        print("✅ CoreOverrideScreen.kt 已重写：新增实时状态显示 (logText)！")

print("🎉 注入完毕！直接执行 ./gradlew assembleDebug 编译即可！")
