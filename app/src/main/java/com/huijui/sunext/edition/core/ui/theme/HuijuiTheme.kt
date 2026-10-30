package com.huijui.sunext.edition.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun HuijuiTheme(content: @Composable () -> Unit) {
    // 彻底脱离 MaterialTheme，接管全局背景和颜色
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        content()
    }
}
