package com.huijui.su.next.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.component.SmartBackground

@Composable
fun ToolboxScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Text(
            text = "HuiJuiSU 纯血引擎已启动！",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
