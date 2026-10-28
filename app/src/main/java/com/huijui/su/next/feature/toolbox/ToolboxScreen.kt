package com.huijui.su.next.feature.toolbox

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ToolboxScreen(viewModel: ToolboxViewModel = viewModel()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("HuiJuiSU Next Edition", color = Color.White, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("全新 Clean 架构隔离测试", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("设备状态: ${viewModel.deviceModel}", color = Color.Green, fontSize = 16.sp)
    }
}
