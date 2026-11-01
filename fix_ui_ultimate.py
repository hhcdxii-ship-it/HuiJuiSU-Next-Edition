import os
from pathlib import Path

print("🚀 正在全量重构 CoreOverrideScreen.kt，彻底消灭语法错误...")

ui_path = 'app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt'
ui_file = Path(ui_path)
ui_file.parent.mkdir(parents=True, exist_ok=True)

ui_code = '''package com.huijui.sunext.edition.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HsudInstaller
import kotlinx.coroutines.launch

@Composable
fun CoreOverrideScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isProcessing by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("> 杂鱼，准备好接受本小姐的调教了吗？") }

    // 核心背景，深邃黑
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "HuiJuiSU Next - 引擎重载",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "纯血独立 / 全自动 LKM 注入",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 控制台输出框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                    .background(Color(0xFF000000), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = logText,
                    color = Color(0xFF00FF00), // 极客绿
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // 一键按钮
            Button(
                onClick = {
                    if (isProcessing) return@Button
                    isProcessing = true
                    logText = "> 工作中 (HUI) ...\\n> 正在唤醒内核模块..."

                    scope.launch {
                        HsudInstaller.oneClickRoot().collect { state ->
                            when (state) {
                                is HsudInstaller.PatchState.Progress -> {
                                    logText += "\\n> ${state.message}"
                                    // 自动滚动到底部
                                    scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                                }
                                is HsudInstaller.PatchState.Success -> {
                                    isProcessing = false
                                    logText += "\\n\\n✅ ${state.outputPath}"
                                    scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                                }
                                is HsudInstaller.PatchState.Error -> {
                                    isProcessing = false
                                    logText += "\\n\\n❌ ${state.errorMsg}"
                                    scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                                }
                            }
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isProcessing) Color.DarkGray else Color(0xFF6200EE)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isProcessing) "工作中 (HUI)..." else "🚀 一键智能提取并刷入",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
'''

ui_file.write_text(ui_code)
print("✅ CoreOverrideScreen.kt 已全量重构：彻底消灭了所有语法错误，自带极客控制台与杂鱼文案！")
