package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.language.ZakoText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HuijuiScriptRunner
import com.huijui.sunext.edition.core.root.RootEngineVerifier
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.launch

@Composable
fun HuijuiHotInstallCard(onFinished: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (isRunning) return@GlassCard
            isRunning = true
            lastResult = null
            scope.launch {
                val result = HuijuiScriptRunner.runHotInstall(context)
                
                // 🚀 真实联动：不管成功还是失败，立刻重新查验内核状态
                val verifyReport = RootEngineVerifier.verifyDetailed()
                
                val summary = if (result.isSuccess && verifyReport.isActive) {
                    AppSettings.hasRoot = true
                    "✅ 热接管成功：HUI 已激活！"
                } else {
                    AppSettings.hasRoot = false
                    "❌ 接管失败：${verifyReport.detail}"
                }
                
                lastResult = summary
                isRunning = false
                onFinished()
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(
                    "⚡ 极速热接管",
                    color = AppSettings.accentColor.color,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                ZakoText(
                    lastResult ?: if (isRunning) "正在注入 HUI 内核，请稍候…" else "点击拉起 HUI 内核接管底层",
                    color = if (lastResult?.startsWith("❌") == true) Color(0xFFEF5350) else Color.White.copy(0.8f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = AppSettings.accentColor.color
                )
            }
        }
    }
}
