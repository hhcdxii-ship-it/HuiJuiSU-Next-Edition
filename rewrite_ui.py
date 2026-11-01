import os
from pathlib import Path

print("🚀 正在重写 CoreOverrideScreen 的极客毛玻璃 UI...")

ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HsudInstaller
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.language.zakoString
import kotlinx.coroutines.launch

@Composable
fun CoreOverrideScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isProcessing by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("> " + zakoString("杂鱼，准备好接受本小姐的调教了吗？")) }
    
    // 状态：是否拥有 Root 权限
    var hasRoot by remember { mutableStateOf(false) }

    // 启动时检查一次 Root
    LaunchedEffect(Unit) {
        hasRoot = HuijuiShell.exec("su -c id", useCache = false).isSuccess
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11)) // 深邃背景
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题部分
            ZakoText(
                text = "HuiJuiSU Next - 底层重载",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            ZakoText(
                text = "纯血独立 / 全自动 LKM 注入",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 控制台输出框 (固定高度，不占满全屏，留空间给按钮)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color(0xFF2C2C35), RoundedCornerShape(12.dp))
                    .background(Color(0xFF050505), RoundedCornerShape(12.dp))
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
            
            Spacer(modifier = Modifier.height(16.dp))

            // 卡片 1：一键提取并刷入
            ActionCard(
                title = "🚀 一键提取并刷入",
                desc = "从底层拉取原厂镜像，打补丁后直接写回。",
                enabled = !isProcessing && hasRoot,
                buttonColor = Color(0xFF6200EE),
                onClick = {
                    isProcessing = true
                    logText += "\\n> " + zakoString("工作中 (HUI)...")
                    scope.launch {
                        HsudInstaller.oneClickRoot().collect { state ->
                            when (state) {
                                is HsudInstaller.PatchState.Progress -> {
                                    logText += "\\n> ${state.message}"
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
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // 卡片 2：仅修补并保存
            ActionCard(
                title = "💾 本地镜像修补 (仅保存)",
                desc = "选择本地 boot/init_boot.img 修补，输出至 Download。",
                enabled = !isProcessing,
                buttonColor = Color(0xFF2C2C35),
                onClick = {
                    // 占位逻辑：后续可对接系统文件选择器
                    logText += "\\n> 请前往“刷入镜像”页面选择本地镜像进行修补。"
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 卡片 3：OTA 槽位更新
            ActionCard(
                title = "🔄 OTA 槽位更新",
                desc = "为下一次系统 OTA 准备，修补空闲槽位。",
                enabled = !isProcessing && hasRoot,
                buttonColor = Color(0xFF2C2C35),
                onClick = {
                    // 占位逻辑：需要底层支持写入另一个槽位
                    logText += "\\n> " + zakoString("OTA 槽位修补即将上线，笨蛋杂鱼再等等吧。")
                }
            )
            
            // Root 状态提示
            if (!hasRoot) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ 未获取 Root 权限，部分功能已锁定",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    desc: String,
    enabled: Boolean,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E24)) // 毛玻璃感的深灰
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            ZakoText(
                text = title,
                color = if (enabled) Color.White else Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            ZakoText(
                text = desc,
                color = if (enabled) Color.LightGray else Color.DarkGray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    disabledContainerColor = Color(0xFF333333)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = if (enabled) "执行" else "锁定", fontSize = 14.sp)
            }
        }
    }
}
'''

ui_path.write_text(ui_code)
print("✅ CoreOverrideScreen.kt 已重构：毛玻璃卡片风格 + Root状态锁定 + 本地/OTA占位入口！")
