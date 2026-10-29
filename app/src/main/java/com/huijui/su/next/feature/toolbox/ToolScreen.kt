package com.huijui.su.next.feature.toolbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.layout.HuijuiLayout
import com.huijui.su.next.core.utils.AppSettings

@Composable
fun ToolScreen(
    onNavigateToConsole: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToCpu: () -> Unit = {},
    onNavigateToTweaks: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onNavigateToHardware: () -> Unit = {},
    onNavigateToSensors: () -> Unit = {},
    onNavigateToScreenTest: () -> Unit = {},
    onNavigateToQuickAction: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToArk: () -> Unit = {},
    onNavigateToHiding: () -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal),
        contentPadding = PaddingValues(top = HuijuiLayout.ScreenTop, bottom = HuijuiLayout.ScreenBottom),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(4) }) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("调优与工具", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("Advanced Tools", color = Color.White.copy(0.4f), fontSize = 12.sp, letterSpacing = 1.sp)
            }
        }

        item(span = { GridItemSpan(4) }) { SectionTitle("日常管理") }
        item { BentoIcon(Icons.Default.Bolt, "快捷指令", onNavigateToQuickAction) }
        item { BentoIcon(Icons.Default.Apps, "App 管理", onNavigateToApps) }
        item { BentoIcon(Icons.Default.BugReport, "Root 审计", onNavigateToAudit) }
        item { BentoIcon(Icons.Default.Shield, "权限审计", onNavigateToPermissions) }
        item { BentoIcon(Icons.Default.AllInbox, "配置方舟", onNavigateToArk) }
        item { BentoIcon(Icons.Default.VisibilityOff, "隐藏防御", onNavigateToHiding) }
        item {} // 占位对齐
        item { BentoIcon(Icons.Default.AllInbox, "配置方舟", onNavigateToArk) }
        item { BentoIcon(Icons.Default.VisibilityOff, "隐藏防御", onNavigateToHiding) }
        item {} // 占位对齐

        item(span = { GridItemSpan(4) }) { SectionTitle("系统性能") }
        item { BentoIcon(Icons.Default.Speed, "CPU 调度", onNavigateToCpu) }
        item { BentoIcon(Icons.Default.Security, "系统魔改", onNavigateToTweaks) }
        item { BentoIcon(Icons.Default.SaveAlt, "分区备份", onNavigateToBackup) }
        item { BentoIcon(Icons.Default.Terminal, "终端命令", onNavigateToConsole) }

        item(span = { GridItemSpan(4) }) { SectionTitle("设备与测试") }
        item { BentoIcon(Icons.Default.Info, "硬件信息", onNavigateToHardware) }
        item { BentoIcon(Icons.Default.Sensors, "传感器", onNavigateToSensors) }
        item { BentoIcon(Icons.Default.PhoneAndroid, "屏幕测试", onNavigateToScreenTest) }
        item {} // 留空以保持网格对齐
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White.copy(0.7f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun BentoIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "bento")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(AppSettings.accentColor.color.copy(alpha = 0.25f), AppSettings.accentColor.color.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White.copy(0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
