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
import com.huijui.su.next.core.utils.MaterialStyle

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
    val currentStyle = AppSettings.materialStyle

    // 🚀 根据材质风格计算 Modifier
    val styleModifier = when (currentStyle) {
        MaterialStyle.GLASS -> {
            Modifier
                .shadow(elevation = 15.dp, shape = cornerRadius, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
                .clip(cornerRadius)
                .background(Color.White.copy(alpha = 0.08f))
                .background(Brush.linearGradient(listOf(Color.White.copy(0.25f), Color.White.copy(0.05f), Color.Transparent)))
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.6f), Color.White.copy(0.1f), Color.Black.copy(0.2f), Color.White.copy(0.2f))), cornerRadius)
        }
        MaterialStyle.SOLID -> {
            Modifier
                .shadow(elevation = 8.dp, shape = cornerRadius, ambientColor = Color.Black.copy(0.5f), spotColor = Color.Black.copy(0.5f))
                .clip(cornerRadius)
                .background(Color(0xFF1E1E1E).copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(0.1f), cornerRadius)
        }
        MaterialStyle.NONE -> {
            Modifier
                // 极简：完全没有背景和边框
                .clip(cornerRadius)
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .then(styleModifier)
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
