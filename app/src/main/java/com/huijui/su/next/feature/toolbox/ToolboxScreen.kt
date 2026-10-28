package com.huijui.su.next.feature.toolbox

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.ui.component.SmartBackground
import kotlinx.coroutines.launch

data class ToolItem(val id: String, val icon: ImageVector, val title: String, val desc: String)

@Composable
fun ToolboxScreen(viewModel: ToolboxViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tools = listOf(
        ToolItem("clean", Icons.TwoTone.CleaningServices, "极速清理内存", "底层 drop_caches 释放 RAM"),
        ToolItem("sysui", Icons.TwoTone.Refresh, "重启 SystemUI", "专治状态栏卡死"),
        ToolItem("flash", Icons.TwoTone.FlashOn, "一键刷机", "全自动向导页面"),
        ToolItem("temp_root", Icons.TwoTone.Lock, "临时 Root", "KMI 无痕注入"),
        ToolItem("repo", Icons.TwoTone.Archive, "模块仓库", "纯净模块"),
        ToolItem("terminal", Icons.TwoTone.Terminal, "高级终端", "实时 Shell 执行"),
        ToolItem("backup", Icons.TwoTone.Security, "分区备份", "保护 boot / init_boot")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.7f)))
            )
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(48.dp))

            BasicText(
                text = "HuiJuiSU Next Edition",
                style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            )
            BasicText(
                text = "五合一架构 · 性能美学",
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp),
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(viewModel.rootStatus, style = TextStyle(color = if (viewModel.rootStatus.contains("已")) Color.Green else Color.Red, fontSize = 12.sp))
                BasicText("已装载模块: ${viewModel.moduleCount}", style = TextStyle(color = Color.White, fontSize = 12.sp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(tools, key = { it.id }) { tool ->
                    GlassToolCard(tool) {
                        scope.launch {
                            when (tool.id) {
                                "clean" -> {
                                    HuijuiShell.exec("sync; echo 3 > /proc/sys/vm/drop_caches")
                                    Toast.makeText(context, "内存已极速清理", Toast.LENGTH_SHORT).show()
                                }
                                "sysui" -> {
                                    HuijuiShell.exec("pkill -f com.android.systemui")
                                    Toast.makeText(context, "SystemUI 重启指令已发送", Toast.LENGTH_SHORT).show()
                                }
                                else -> Toast.makeText(context, "${tool.title} 正在开发中", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassToolCard(tool: ToolItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 纯血极客弹簧动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "card_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.03f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Image(
                painter = rememberVectorPainter(tool.icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(Color(0xFF81C784))
            )
            Column {
                BasicText(tool.title, style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(tool.desc, style = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp))
            }
        }
    }
}
