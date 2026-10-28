package com.huijui.su.next.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huijui.su.next.core.utils.AppSettings

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    val cornerRadius = RoundedCornerShape(AppSettings.cardCornerRadius.dp)

    Box(
        modifier = modifier
            .scale(scale)
            // 1. 外阴影，增加悬浮立体感
            .shadow(elevation = 12.dp, shape = cornerRadius, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
            .clip(cornerRadius)
            // 2. 🚀 核心修复：加一层深灰黑色底裤（alpha 0.5f），既保留透明感，又保证文字对比度
            .background(Color(0xFF1A1A1A).copy(alpha = 0.5f))
            // 3. 🚀 再叠一层微弱的白色渐变，模拟玻璃的高光反射
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.1f), Color.Transparent)))
            // 4. 高光描边（左上方亮，右下方暗，体现玻璃厚度）
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(0.75f),
                        Color.White.copy(0.1f),
                        Color.Transparent,
                        Color.White.copy(0.05f)
                    )
                ),
                shape = cornerRadius
            )
            .then(
                if (onClick != null && onClick != {}) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(20.dp)
    ) {
        content()
    }
}
