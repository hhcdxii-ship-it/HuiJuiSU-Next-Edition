package com.huijui.su.next.feature.toolbox

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.huijui.su.next.core.utils.AppSettings

enum class ScreenTestMode(val label: String) {
    DEAD_PIXEL("坏点"),
    TOUCH("触控"),
    GRAYSCALE("灰阶")
}

/**
 * 🖥️ 屏幕测试（验机必用）
 *
 * 三种模式：
 *   1. 坏点检测：9 种纯色全屏轮播，点击切换，长按退出
 *   2. 触控测试：12×20 网格，手指划过变绿（支持多点触控）
 *   3. 灰阶测试：16 级灰度条，检查屏幕灰阶过渡
 *
 * 沉浸模式：
 *   - 进入页面自动隐藏状态栏 + 导航栏
 *   - 离开页面自动恢复
 *   - 顶部工具栏可点击隐藏（沉浸观感）
 */
@Composable
fun ScreenTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var mode by remember { mutableStateOf(ScreenTestMode.DEAD_PIXEL) }
    var showToolbar by remember { mutableStateOf(true) }

    // 沉浸模式
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldBehavior = controller?.systemBarsBehavior
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (oldBehavior != null) controller.systemBarsBehavior = oldBehavior
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            ScreenTestMode.DEAD_PIXEL -> DeadPixelTest(onExit = onBack)
            ScreenTestMode.TOUCH -> TouchTest(onExit = onBack)
            ScreenTestMode.GRAYSCALE -> GrayscaleTest(onExit = onBack)
        }

        // 顶部模式切换工具栏
        AnimatedVisibility(
            visible = showToolbar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(0.7f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScreenTestMode.values().forEach { m ->
                    val isSelected = m == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) AppSettings.accentColor.color.copy(alpha = 0.3f)
                                else Color.White.copy(0.08f)
                            )
                            .pointerInput(m) {
                                detectTapGestures { mode = m }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            m.label,
                            color = if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 底部操作提示
        AnimatedVisibility(
            visible = showToolbar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                when (mode) {
                    ScreenTestMode.DEAD_PIXEL -> "点击切换颜色 · 长按退出"
                    ScreenTestMode.TOUCH -> "手指划过网格 · 长按退出"
                    ScreenTestMode.GRAYSCALE -> "检查灰阶过渡 · 长按退出"
                },
                color = Color.White.copy(0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ==========================================
// 1. 坏点检测（9 种纯色全屏）
// ==========================================
@Composable
private fun DeadPixelTest(onExit: () -> Unit) {
    val colors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.White,
        Color.Black,
        Color(0xFF888888),
        Color(0xFFFFFF00),  // 黄
        Color(0xFF00FFFF),  // 青
        Color(0xFFFF00FF)   // 品红
    )
    var currentIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors[currentIndex])
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        currentIndex = (currentIndex + 1) % colors.size
                    },
                    onLongPress = {
                        onExit()
                    }
                )
            }
    )
}

// ==========================================
// 2. 触控测试（12×20 网格）
// ==========================================
@Composable
private fun TouchTest(onExit: () -> Unit) {
    val gridCols = 12
    val gridRows = 20
    val touched = remember { mutableStateMapOf<Int, Boolean>() }
    val touchedCount = touched.count { it.value }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(gridRows) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(gridCols) { col ->
                        val key = row * gridCols + col
                        val isTouched = touched[key] == true
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isTouched) Color(0xFF00FF00) else Color(0xFF1A1A1A))
                                .border(0.5.dp, Color.White.copy(0.08f))
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach {
                                                if (it.pressed) touched[key] = true
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // 右上角显示触控统计（点击长按退出）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(0.7f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "$touchedCount / ${gridCols * gridRows}",
                color = Color(0xFF00FF00),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 长按退出（右下角区域）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onExit() })
                }
        )
    }
}

// ==========================================
// 3. 灰阶测试（16 级）
// ==========================================
@Composable
private fun GrayscaleTest(onExit: () -> Unit) {
    val levels = 16
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onExit() })
            }
    ) {
        repeat(levels) { i ->
            val gray = (i * 255 / (levels - 1))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(gray, gray, gray)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "$gray",
                    color = if (gray > 128) Color.Black.copy(0.6f) else Color.White.copy(0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
