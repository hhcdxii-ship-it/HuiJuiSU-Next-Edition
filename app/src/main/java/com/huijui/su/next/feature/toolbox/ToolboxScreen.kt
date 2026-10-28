package com.huijui.su.next.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.component.SmartBackground

data class ToolItem(val icon: ImageVector, val title: String, val desc: String)

@Composable
fun ToolboxScreen() {
    val tools = listOf(
        ToolItem(Icons.TwoTone.CleaningServices, "极速清理", "底层 drop_caches 释放 RAM"),
        ToolItem(Icons.TwoTone.Refresh, "重启 UI", "专治状态栏卡死"),
        ToolItem(Icons.TwoTone.FlashOn, "一键刷机", "全自动向导页面"),
        ToolItem(Icons.TwoTone.Terminal, "高级终端", "实时 Shell 执行"),
        ToolItem(Icons.TwoTone.Archive, "模块仓库", "纯净模块"),
        ToolItem(Icons.TwoTone.Security, "分区备份", "保护 boot / init_boot")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        // 暗色遮罩让文字更清晰
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(0.7f)))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(64.dp))
            Text("HuiJuiSU", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text("Next Edition · 纯血架构", color = Color.White.copy(0.7f), fontSize = 14.sp)

            Spacer(modifier = Modifier.height(32.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp) // 给底栏留出空间
            ) {
                items(tools.size) { index ->
                    val tool = tools[index]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.White.copy(0.05f))))
                            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(20.dp))
                            .clickable { }
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(tool.icon, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(28.dp))
                            Column {
                                Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(tool.desc, color = Color.White.copy(0.5f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
