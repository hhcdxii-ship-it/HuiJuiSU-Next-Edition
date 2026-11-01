import os
from pathlib import Path

print("🚀 正在用矢量图标（Icons）替换丑陋的 Emoji...")

ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')

ui_code = '''package com.huijui.sunext.edition.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    var hasRoot by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasRoot = HuijuiShell.exec("su -c id", useCache = false).isSuccess
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

            // 卡片 1：用 RocketLaunch 替换 🚀
            PremiumGlassCard(
                icon = Icons.Default.RocketLaunch,
                title = "一键提取并刷入",
                desc = "从底层拉取原厂镜像，打补丁后直接写回。",
                enabled = !isProcessing && hasRoot,
                actionText = "执行",
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
                                }
                                is HsudInstaller.PatchState.Error -> {
                                    isProcessing = false
                                    logText += "\\n\\n❌ ${state.errorMsg}"
                                }
                            }
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // 卡片 2：用 Save 替换 💾
            PremiumGlassCard(
                icon = Icons.Default.Save,
                title = "本地镜像修补",
                desc = "选择本地 boot/init_boot.img 修补，仅保存至 Download。",
                enabled = !isProcessing,
                actionText = "选择文件",
                onClick = {
                    logText += "\\n> 请前往“刷入镜像”页面选择本地镜像。"
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 卡片 3：用 Sync 替换 🔄
            PremiumGlassCard(
                icon = Icons.Default.Sync,
                title = "OTA 槽位更新",
                desc = "为下一次系统 OTA 准备，修补空闲槽位。",
                enabled = !isProcessing && hasRoot,
                actionText = "执行",
                onClick = {
                    logText += "\\n> " + zakoString("OTA 槽位修补即将上线。")
                }
            )
            
            if (!hasRoot) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "⚠️ 未获取 Root 权限，部分功能已锁定",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PremiumGlassCard(
    icon: ImageVector, // 接收矢量图标
    title: String,
    desc: String,
    enabled: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.25f else 0.1f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧矢量图标
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(
                    text = title,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                ZakoText(
                    text = desc,
                    color = if (enabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF6200EE).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = actionText,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
'''

ui_path.write_text(ui_code)
print("✅ 极简矢量图标注入成功！Emoji 时代彻底结束！")
