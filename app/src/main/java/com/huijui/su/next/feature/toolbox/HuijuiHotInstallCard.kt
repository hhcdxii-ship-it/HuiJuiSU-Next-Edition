package com.huijui.su.next.feature.toolbox

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
import com.huijui.su.next.core.root.HuijuiScriptRunner
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.launch

@Composable
fun HuijuiHotInstallCard(onFinished: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    GlassCard(
        // 换成最小高度，让它自适应
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        onClick = {
            if (isRunning) return@GlassCard
            isRunning = true
            lastResult = null
            scope.launch {
                val result = HuijuiScriptRunner.runHotInstall(context)
                val summary = if (result.isSuccess) {
                    "✅ 热接管成功：" + result.out.takeLast(2).joinToString(" ")
                } else {
                    "❌ 接管失败：" + (result.err.firstOrNull() ?: result.out.lastOrNull() ?: "未知错误")
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
                Text(
                    "⚡ 极速热接管",
                    color = AppSettings.accentColor.color,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    lastResult ?: if (isRunning) "接管中，请稍候…" else "点击拉起 HuiJuiSU 守护进程，无需重启",
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
