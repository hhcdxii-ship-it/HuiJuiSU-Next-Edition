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
            // 1. 外阴影，制造深度的物理厚度
            .shadow(elevation = 15.dp, shape = cornerRadius, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .clip(cornerRadius)
            // 2. 🚀 灵魂底色：极其微弱的白色填充（替代死黑底）
            // 依靠壁纸的底色透过来，形成自然的环境光折射
            .background(Color.White.copy(alpha = 0.08f))
            // 3. 🚀 液态高光：左上角受光面，向右下角逐渐消失的白色渐变
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            // 4. 边缘高光描边：模拟玻璃边缘的切角反光
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f), // 顶部强高光
                        Color.White.copy(alpha = 0.1f),
                        Color.Black.copy(alpha = 0.2f), // 底部暗边缘
                        Color.White.copy(alpha = 0.2f)
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
