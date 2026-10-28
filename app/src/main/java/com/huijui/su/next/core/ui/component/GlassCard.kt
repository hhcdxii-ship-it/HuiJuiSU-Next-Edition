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

    // 🚀 动态圆角
    val cornerRadius = RoundedCornerShape(AppSettings.cardCornerRadius.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = 12.dp, shape = cornerRadius, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
            .shadow(elevation = 2.dp, shape = cornerRadius, ambientColor = Color.White.copy(0.1f), spotColor = Color.White.copy(0.1f))
            .clip(cornerRadius)
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color.White.copy(0.04f))))
            .border(0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(0.4f), Color.White.copy(0.05f))), cornerRadius)
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
