package com.huijui.sunext.edition.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavItem(val route: String, val title: String, val icon: ImageVector)

@Composable
fun LiquidNavigationBar(
    items: List<NavItem>,
    currentRoute: String?,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val barWidth = screenWidthDp - 40.dp
    val itemWidth = barWidth / items.size
    val itemWidthPx = with(density) { itemWidth.toPx() }

    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val accentColor = AppSettings.accentColor.color

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedBaseX by animateFloatAsState(
        targetValue = selectedIndex * itemWidthPx,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
        label = "indicatorX"
    )

    val currentX = if (isDragging) (selectedIndex * itemWidthPx + dragOffset) else animatedBaseX

    val dragFraction = if (itemWidthPx > 0f) (dragOffset / itemWidthPx).coerceIn(-1.2f, 1.2f) else 0f
    val stretchX by animateFloatAsState(
        targetValue = if (isDragging) 1f + abs(dragFraction) * 0.4f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "stretchX"
    )
    val stretchY by animateFloatAsState(
        targetValue = if (isDragging) 1f - abs(dragFraction) * 0.2f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "stretchY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            // 🚀 满血浅色毛玻璃：高透白底 + 微弱暗角
            .background(Color.White.copy(alpha = 0.12f))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f), // 顶部强高光
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.05f)   // 底部轻微暗角增加立体感
                    )
                )
            )
            // 🚀 玻璃反光边框（更亮、更通透）
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.8f),
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(36.dp)
            )
            .pointerInput(items.size, selectedIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true; dragOffset = 0f },
                    onDragEnd = {
                        isDragging = false
                        val dragItems = (dragOffset / itemWidthPx).roundToInt()
                        val newIndex = (selectedIndex + dragItems).coerceIn(0, items.size - 1)
                        if (newIndex != selectedIndex) onItemSelected(items[newIndex].route)
                        dragOffset = 0f
                    },
                    onDragCancel = { isDragging = false; dragOffset = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                )
            }
    ) {
        // 🚀 液态气泡：高对比度叠加
        Canvas(modifier = Modifier.fillMaxSize().zIndex(0f)) {
            val padX = 8.dp.toPx()
            val padY = 12.dp.toPx()
            val baseW = itemWidthPx - padX * 2
            val baseH = size.height - padY * 2
            val scaledW = baseW * stretchX
            val scaledH = baseH * stretchY
            val centerX = currentX + itemWidthPx / 2f
            val centerY = size.height / 2f
            val left = centerX - scaledW / 2f
            val top = centerY - scaledH / 2f

            // 画玻璃底色（半透明主题色）
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0.2f)
                    )
                ),
                topLeft = Offset(left, top),
                size = Size(scaledW, scaledH),
                cornerRadius = CornerRadius(scaledH / 2f)
            )

            // 画内部高光反光
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    startY = top,
                    endY = top + scaledH / 2f
                ),
                topLeft = Offset(left, top),
                size = Size(scaledW, scaledH / 2f),
                cornerRadius = CornerRadius(scaledH / 2f)
            )
        }

        // 图标层
        Row(modifier = Modifier.fillMaxSize().zIndex(1f)) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                    label = "iconScale_$index"
                )
                // 🚀 浅色玻璃下，未选中的纯灰会显脏，改为半透明白，与玻璃融合
                val color by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                    animationSpec = tween(300),
                    label = "color_$index"
                )

                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = color,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ZakoText(
                            text = item.title,
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
