package com.huijui.sunext.edition.core.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.huijui.sunext.edition.core.ui.effect.huiEntrance
import com.huijui.sunext.edition.core.ui.effect.huiShimmer
import com.huijui.sunext.edition.core.ui.theme.HuiTheme
import com.huijui.sunext.edition.core.utils.AnimationStyle
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.MaterialStyle

/** 子页面（工具箱 / WebUI）里强制走更实的玻璃，避免大面积视频背景干扰阅读。 */
val LocalStrongGlass = compositionLocalOf { false }

/**
 * 全局统一的卡片容器。
 *
 * 与旧版的区别（都是实打实的 bug）：
 *   1. 旧版把 `materialStyle` / `animationStyle` / `animationSpeedScale` 三个设置项
 *      存在 SharedPreferences 里但**从来没读过** —— 用户在设置页来回切，界面纹丝不动。
 *      现在这三项真正驱动卡片外观与弹簧参数。
 *   2. 旧版只有 `Color.White.copy(0.08f)` 一层平涂，没有折光，所谓「玻璃」其实是块灰板。
 *      现在用 顶面高光 → 主体 → 底部压暗 的三段渐变，再加一道左上角斜向反光。
 *   3. 按下时整卡缩放会连内容一起糊掉；改为按压缩放 + 描边提亮，文字保持锐利。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val glass = HuiTheme.glass
    val style = AppSettings.materialStyle
    val strong = LocalStrongGlass.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val spec = remember(AppSettings.animationStyle, AppSettings.animationSpeedScale) {
        pressSpec(AppSettings.animationStyle, AppSettings.animationSpeedScale)
    }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.972f else 1f,
        animationSpec = spec,
        label = "card_scale"
    )

    val cornerRadius = RoundedCornerShape(AppSettings.cardCornerRadius.dp)
    val fillAlphaBoost = if (strong) 1.6f else 1f
    val isGlassLike = style == MaterialStyle.GLASS ||
        style == MaterialStyle.LIQUID_GLASS ||
        style == MaterialStyle.SOFT_GLASS

    val fill = when (style) {
        MaterialStyle.LIQUID_GLASS -> Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f * fillAlphaBoost),
                glass.fillTop,
                Color.White.copy(alpha = 0.05f),
            )
        )
        MaterialStyle.SOFT_GLASS -> Brush.verticalGradient(
            colors = listOf(
                glass.fillTop.copy(alpha = (glass.fillTop.alpha * fillAlphaBoost).coerceAtMost(0.5f)),
                glass.fillBottom.copy(alpha = (glass.fillBottom.alpha * fillAlphaBoost).coerceAtMost(0.5f)),
            )
        )
        MaterialStyle.GLASS -> Brush.verticalGradient(
            colors = listOf(
                glass.fillTop.copy(alpha = (glass.fillTop.alpha * fillAlphaBoost).coerceAtMost(0.9f)),
                glass.fillBottom.copy(alpha = (glass.fillBottom.alpha * fillAlphaBoost).coerceAtMost(0.9f)),
            )
        )
        MaterialStyle.SOLID -> Brush.verticalGradient(listOf(glass.fillTop, glass.fillBottom))
        MaterialStyle.NONE -> null
    }

    Box(
        modifier = modifier
            // 入场：淡入 + 轻微上浮。所有卡片共用同一条曲线，
            // 列表滚动时逐张浮现，不会出现「有的卡有动画、有的没有」。
            .huiEntrance()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(cornerRadius)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(
                if (style != MaterialStyle.NONE) {
                    Modifier.border(
                        width = when (style) {
                            MaterialStyle.LIQUID_GLASS -> 1.2.dp
                            MaterialStyle.SOFT_GLASS -> 0.8.dp
                            else -> 1.dp
                        },
                        color = if (isPressed) glass.border.copy(alpha = 0.42f) else glass.border,
                        shape = cornerRadius
                    )
                } else Modifier
            )
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
    ) {
        // 液态玻璃：一道持续流动的高光带，模拟液体表面的张力弧。
        // 用 shimmer 而不是新写动画，保证它同样受「关闭动画」开关控制。
        if (style == MaterialStyle.LIQUID_GLASS) {
            Box(modifier = Modifier.matchParentSize().huiShimmer(Color.White))
        }

        // 左上角斜向反光：真实玻璃最有辨识度的一笔，成本只有一个 draw call。
        if (isGlassLike && style != MaterialStyle.SOFT_GLASS) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                glass.sheen,
                                Color.Transparent,
                                Color.Transparent,
                            )
                        )
                    )
            )
        }

        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

private fun pressSpec(style: AnimationStyle, rawSpeed: Float): AnimationSpec<Float> {
    val speed = rawSpeed.coerceIn(0.4f, 2.5f)
    return when (style) {
        AnimationStyle.SPRING -> spring(dampingRatio = 0.55f, stiffness = 520f * speed)
        AnimationStyle.SMOOTH -> tween(durationMillis = (190f / speed).toInt().coerceAtLeast(40))
        AnimationStyle.NONE -> snap()
    }
}
