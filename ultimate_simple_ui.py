from pathlib import Path
import re

print("🚀 正在重写极简 UI，彻底剥离所有 su 残留...")

# 1. 全盘扫射，把可能残留的 su 彻底干掉
for kt in Path('app/src/main/java').rglob('*.kt'):
    if kt.name == 'HuijuiShell.kt':
        c = kt.read_text()
        c = c.replace('"su", "-c"', '"sh", "-c"')
        c = c.replace('ProcessBuilder("su"', 'ProcessBuilder("sh"')
        kt.write_text(c)

# 2. 重写 CoreOverrideScreen.kt
path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')

ui_code = '''package com.huijui.sunext.edition.feature.toolbox

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HsudInstaller
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.language.zakoString
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CoreOverrideScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isProcessing by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("> " + zakoString("杂鱼，准备好接受本小姐的调教了吗？")) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = File(context.cacheDir, "local_patch_temp.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    logText += "\\n> ❌ 读取的文件为空或不存在！"
                    return@rememberLauncherForActivityResult
                }

                val sizeMB = tempFile.length() / (1024 * 1024)
                val targetPart = if (sizeMB > 30) "boot" else "init_boot"
                val outPath = "/sdcard/Download/huijui_patched_${System.currentTimeMillis()}.img"
                
                logText += "\\n> 已选择镜像，大小: ${sizeMB}MB，准备修补..."
                
                isProcessing = true
                scope.launch {
                    HsudInstaller.patchImage(context, tempFile.absolutePath, targetPart, outPath).collect { state ->
                        when (state) {
                            is HsudInstaller.PatchState.Progress -> {
                                logText += "\\n> " + state.message
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            is HsudInstaller.PatchState.Success -> {
                                isProcessing = false
                                logText += "\\n\\n✅ 本地修补成功！已保存至: " + state.outputPath
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            is HsudInstaller.PatchState.Error -> {
                                isProcessing = false
                                logText += "\\n\\n❌ 修补失败: " + state.errorMsg
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logText += "\\n\\n❌ 文件处理异常: " + e.message
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZakoText(
                text = "HuiJuiSU Next - 底层重载",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            ZakoText(
                text = "纯血独立 / 全自动 LKM 注入",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F1115).copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = logText,
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // 巨大的、唯一的按钮
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE),
                    disabledContainerColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                ZakoText(
                    text = if (isProcessing) "正在处理中..." else "选择本地镜像进行修补",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
'''

path.write_text(ui_code)
print("✅ 极简 UI 重写完成！底部垃圾按钮已彻底物理超度！")
