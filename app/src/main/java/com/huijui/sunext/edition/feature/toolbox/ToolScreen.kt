package com.huijui.sunext.edition.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

@Composable
fun ToolScreen(
    onNavigateToConsole: () -> Unit = {}, onNavigateToBackup: () -> Unit = {}, onNavigateToCpu: () -> Unit = {},
    onNavigateToTweaks: () -> Unit = {}, onNavigateToAudit: () -> Unit = {}, onNavigateToApps: () -> Unit = {},
    onNavigateToHardware: () -> Unit = {}, onNavigateToSensors: () -> Unit = {}, onNavigateToScreenTest: () -> Unit = {},
    onNavigateToQuickAction: () -> Unit = {}, onNavigateToPermissions: () -> Unit = {}, onNavigateToArk: () -> Unit = {},
    onNavigateToHiding: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                ZakoText("调优与工具", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                ZakoText("Advanced Tools", color = Color.White.copy(0.5f), fontSize = 12.sp)
            }
        }

        item {
            ToolGroupCard("日常管理", listOf(
                ToolItem("快捷指令", Icons.Default.Bolt, onNavigateToQuickAction),
                ToolItem("App 管理", Icons.Default.Apps, onNavigateToApps),
                ToolItem("Root 审计", Icons.Default.BugReport, onNavigateToAudit),
                ToolItem("权限审计", Icons.Default.Shield, onNavigateToPermissions)
            ))
        }

        item {
            ToolGroupCard("系统性能", listOf(
                ToolItem("CPU 调度", Icons.Default.Speed, onNavigateToCpu),
                ToolItem("系统魔改", Icons.Default.Security, onNavigateToTweaks),
                ToolItem("分区备份", Icons.Default.SaveAlt, onNavigateToBackup),
                ToolItem("终端命令", Icons.Default.Terminal, onNavigateToConsole)
            ))
        }

        item {
            ToolGroupCard("设备与测试", listOf(
                ToolItem("硬件信息", Icons.Default.Info, onNavigateToHardware),
                ToolItem("传感器", Icons.Default.Sensors, onNavigateToSensors),
                ToolItem("屏幕测试", Icons.Default.PhoneAndroid, onNavigateToScreenTest)
            ))
        }
    }
}

data class ToolItem(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun ToolGroupCard(title: String, items: List<ToolItem>) {
    Column {
        ZakoText(title, color = Color.White.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { item.onClick() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).background(AppSettings.accentColor.color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        ZakoText(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
