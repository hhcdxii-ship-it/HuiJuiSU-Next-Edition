package com.huijui.su.next.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SmartBackground() {
    // 先不要视频，纯黑背景保底
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)))
}
