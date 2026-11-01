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

/**
 * 工具箱页。
 *
 * 修的 bug：MainActivity 给 ToolScreen 传了 13 个回调，但旧版只用了其中 11 个 ——
 * `onNavigateToArk`（方舟快照）和 `onNavigateToHiding`（隐藏与反检测）**没有任何入口**，
 * 两个完整功能页因此完全不可达。现在补进「进阶玩法」分组。
 *
 * 另外：
 *   - 去掉了重复的 statusBarsPadding（MainActivity 已经加过一次，顶部空白会被顶到两倍高）
 *   - contentPadding 底部 120dp → 16dp（MainActivity 已预留导航栏高度，双重留白是空的）
 */
@Composable
fun ToolScreen(
    onNavigateToConsole: () -> Unit = {}, onNavigateToBackup: () -> Unit = {}, onNavigateToCpu: () -> Unit = {},
    onNavigateToTweaks: () -> Unit = {}, onNavigateToAudit: () -> Unit = {}, onNavigateToApps: () -> Unit = {},
    onNavigateToHardware: () -> Unit = {}, onNavigateToSensors: () -> Unit = {}, onNavigateToScreenTest: () -> Unit = {},
    onNavigateToQuickAction: () -> Unit = {}, onNavigateToPermissions: () -> Unit = {}, onNavigateToArk: () -> Unit = {},
    onNavigateToHiding: () -> Unit = {},
    onNavigateToKernelModules: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                ZakoText("调优与工具", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
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
                ToolItem("终端命令", Icons.Default.Terminal, onNavigateToConsole),
                ToolItem("内核模块", Icons.Default.Memory, onNavigateToKernelModules)
            ))
        }

        item {
            ToolGroupCard("进阶玩法", listOf(
                ToolItem("方舟快照", Icons.Default.Archive, onNavigateToArk),
                ToolItem("隐藏与反检测", Icons.Default.VisibilityOff, onNavigateToHiding)
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
        ZakoText(
            title,
            color = Color.White.copy(0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 52.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.onClick() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(AppSettings.accentColor.color.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                null,
                                tint = AppSettings.accentColor.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        ZakoText(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
