package com.huijui.su.next.core.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.huijui.su.next.core.utils.AppSettings
import com.huijui.su.next.core.utils.MaterialStyle

/**
 * 🎯 强制玻璃模式 CompositionLocal
 *
 * 用途：刷入 / 备份 / 模块安装等"关键操作界面"，
 *       无论用户全局设置什么材质（甚至 SOLID），都强制走强化玻璃。
 *
 * 用法：在路由级或组件级 Provide(true) 即可，不需要每个卡片传参。
 */
val LocalStrongGlass = compositionLocalOf { false }

/**
 * 🎨 全局毛玻璃卡片
 *
 * @param modifier 外部布局修饰符
 * @param onClick 点击回调（null = 不可点击）
 * @param overrideStyle 强制指定材质（null = 跟随全局或 LocalStrongGlass）
 * @param strongGlass 是否强化玻璃（true = 更明显的通透感 + 更亮的边框）
 * @param content 卡片内容
 *
 * 材质优先级（从高到低）：
 *   1. LocalStrongGlass == true → 强制 GLASS + 强化
 *   2. overrideStyle != null → 使用 overrideStyle
 *   3. 全局 AppSettings.materialStyle
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    overrideStyle: MaterialStyle? = null,
    strongGlass: Boolean = false,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    // 🎯 强制玻璃标志（来自路由级或组件级 Provide）
    val forceStrong = LocalStrongGlass.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "card_scale"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 0.2f,
        animationSpec = tween(150),
        label = "border_alpha"
    )

    val cornerRadius = RoundedCornerShape(AppSettings.cardCornerRadius.dp)

    // 🎯 材质优先级：强制玻璃 > 显式 override > 全局设置
    val currentStyle = when {
        forceStrong -> MaterialStyle.GLASS
        overrideStyle != null -> overrideStyle
        else -> AppSettings.materialStyle
    }

    // 🎯 强化优先级：force 或 strongGlass 任一为 true 就强化
    val isStrong = forceStrong || strongGlass

    val styleModifier = when (currentStyle) {
        MaterialStyle.GLASS -> {
            val bgAlpha = if (isStrong) 0.18f else 0.08f
            val gradientStart = if (isStrong) 0.45f else 0.25f
            val gradientMid = if (isStrong) 0.15f else 0.05f
            val borderWidth = if (isStrong) 1.5.dp else 1.dp
            val shadowElevation = if (isStrong) 20.dp else 15.dp
            val borderHighlight = if (isStrong) 0.2f else 0.1f
            val borderBottom = if (isStrong) 0.35f else 0.2f

            Modifier
                .shadow(
                    elevation = if (isPressed) 5.dp else shadowElevation,
                    shape = cornerRadius,
                    ambientColor = Color.Black.copy(0.35f),
                    spotColor = Color.Black.copy(0.35f)
                )
                .clip(cornerRadius)
                .background(Color.White.copy(alpha = bgAlpha))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(gradientStart),
                            Color.White.copy(gradientMid),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    borderWidth,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(borderAlpha),
                            Color.White.copy(borderHighlight),
                            Color.Black.copy(0.2f),
                            Color.White.copy(borderBottom)
                        )
                    ),
                    cornerRadius
                )
        }
        MaterialStyle.SOLID -> {
            Modifier
                .shadow(
                    elevation = if (isPressed) 4.dp else 8.dp,
                    shape = cornerRadius,
                    ambientColor = Color.Black.copy(0.5f),
                    spotColor = Color.Black.copy(0.5f)
                )
                .clip(cornerRadius)
                .background(Color(0xFF1E1E1E).copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(borderAlpha), cornerRadius)
        }
        MaterialStyle.NONE -> {
            Modifier.clip(cornerRadius)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(styleModifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        }
                    )
                } else Modifier
            )
            .padding(20.dp)
    ) {
        content()
    }
}
