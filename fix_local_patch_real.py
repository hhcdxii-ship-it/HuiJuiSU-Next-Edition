import re
from pathlib import Path

print("🚀 正在注入真正的本地文件选择与修补逻辑...")

ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = ui_path.read_text()

# 1. 补全需要的 import
imports = """
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
"""
if 'rememberLauncherForActivityResult' not in content:
    content = content.replace('import androidx.compose.foundation.background', imports + '\nimport androidx.compose.foundation.background')

# 2. 注入文件选择器和修补逻辑
# 找到 state 声明的地方
state_inject = """
    var isProcessing by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("> " + zakoString("杂鱼，准备好接受本小姐的调教了吗？")) }
    var hasRoot by remember { mutableStateOf(false) }

    // 🚀 本地修补专用的状态
    var pendingLocalImgPath by remember { mutableStateOf<String?>(null) }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = File(context.cacheDir, "local_patch_temp.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                pendingLocalImgPath = tempFile.absolutePath
                logText += "\\n> 已选择镜像文件，正在准备修补..."
                
                // 自动判断分区类型并开始修补
                val sizeMB = tempFile.length() / (1024 * 1024)
                val targetPart = if (sizeMB > 30) "boot" else "init_boot"
                
                isProcessing = true
                scope.launch {
                    HsudInstaller.patchImage(
                        context = context,
                        imgPath = tempFile.absolutePath,
                        targetPartition = targetPart,
                        outputPath = "/sdcard/Download/huijui_patched_local_${System.currentTimeMillis()}.img"
                    ).collect { state ->
                        when (state) {
                            is HsudInstaller.PatchState.Progress -> {
                                logText += "\\n> ${state.message}"
                                scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            }
                            is HsudInstaller.PatchState.Success -> {
                                isProcessing = false
                                logText += "\\n\\n✅ 本地修补成功！已保存至: ${state.outputPath}"
                                scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            }
                            is HsudInstaller.PatchState.Error -> {
                                isProcessing = false
                                logText += "\\n\\n❌ 修补失败: ${state.errorMsg}"
                                scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logText += "\\n\\n❌ 读取文件失败: ${e.message}"
            }
        }
    }
"""
content = re.sub(r'    var isProcessing by remember \{ mutableStateOf\(false\) \}\n    var logText by remember \{ mutableStateOf.*?\}\n    var hasRoot by remember \{ mutableStateOf\(false\) \}', state_inject.strip(), content)

# 3. 替换卡片 2 的假逻辑
old_card = r'PremiumGlassCard\(\s*icon\s*=\s*Icons\.Default\.Save,.*?onClick\s*=\s*\{\s*logText \+= ".*?"\s*\}\s*\)'
new_card = """PremiumGlassCard(
                icon = Icons.Default.Save,
                title = "本地镜像修补",
                desc = "选择本地 boot/init_boot.img 修补，仅保存至 Download。",
                enabled = !isProcessing,
                actionText = "选择文件",
                onClick = {
                    // 唤起系统文件选择器，只选 img/octet-stream
                    filePickerLauncher.launch("*/*")
                }
            )"""

content = re.sub(old_card, new_card, content, flags=re.DOTALL)

ui_path.write_text(content)
print("✅ 彻底重写：现在点“选择文件”会直接弹系统文件选择器并自动修补！")
