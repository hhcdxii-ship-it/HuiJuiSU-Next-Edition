package com.huijui.sunext.edition.feature.toolbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.HuiTopBar
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.effect.HUI_DURATIONS
import com.huijui.sunext.edition.core.ui.effect.HuiEasing
import com.huijui.sunext.edition.core.ui.effect.HuiEffectType
import com.huijui.sunext.edition.core.ui.effect.HuiMotion
import com.huijui.sunext.edition.core.ui.effect.huiEffectCombinationCount
import com.huijui.sunext.edition.core.ui.effect.huiGlow
import com.huijui.sunext.edition.core.ui.effect.huiGradientBorder
import com.huijui.sunext.edition.core.ui.effect.huiShimmer
import com.huijui.sunext.edition.core.ui.effect.huiSweep
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 动效实验室。
 *
 * 这里不是「堆一堆随机动画」，而是把动效系统摊开给用户看：
 *   12 种效果 × 8 条缓动 × 12 档时长 = 1152 种组合，
 * 选任意一组都能在下面实时看到结果。
 *
 * 所有效果共用同一套渲染管线，并且受「设置 → 动画速度 & 风格」全局控制，
 * 关掉动画时直接走 snap，不会留一堆跑在后台的无限动画。
 */
@Composable
fun EffectsGalleryScreen(onBack: () -> Unit) {
    val accent = AppSettings.accentColor.color

    var type by remember { mutableStateOf(HuiEffectType.FADE) }
    var easing by remember { mutableStateOf(HuiEasing.FAST_OUT_SLOW_IN) }
    var durationMs by remember { mutableStateOf(400) }

    // 一次铺 12 个相同配置的样例，方便看一致性；不同 index 有 40ms 阶梯
    val demos = remember(type, easing, durationMs) { (0 until 12).toList() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = HuiSpace.s)) {
            HuiTopBar(
                title = "动效实验室",
                subtitle = "MOTION LAB · ${huiEffectCombinationCount()} COMBINATIONS",
                onBack = onBack
            )
        }

        // ── 效果类型 ────────────────────────────────────────────
        ChipRow(
            title = "效果",
            items = HuiEffectType.entries.map { it.label },
            selected = type.label,
            accent = accent
        ) { idx -> type = HuiEffectType.entries[idx] }

        // ── 缓动 ────────────────────────────────────────────────
        ChipRow(
            title = "缓动",
            items = HuiEasing.entries.map { it.label },
            selected = easing.label,
            accent = accent
        ) { idx -> easing = HuiEasing.entries[idx] }

        // ── 时长 ────────────────────────────────────────────────
        ChipRow(
            title = "时长",
            items = HUI_DURATIONS.map { "${it}ms" },
            selected = "${durationMs}ms",
            accent = accent
        ) { idx -> durationMs = HUI_DURATIONS[idx] }

        Spacer(Modifier.height(HuiSpace.s))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = HuiSpace.screen),
            contentPadding = PaddingValues(bottom = HuiSpace.xxl),
            horizontalArrangement = Arrangement.spacedBy(HuiSpace.s),
            verticalArrangement = Arrangement.spacedBy(HuiSpace.s)
        ) {
            items(items = demos, key = { it }) { idx ->
                EffectTile(
                    type = type,
                    easing = easing,
                    durationMs = durationMs,
                    accent = accent,
                    index = idx
                )
            }
        }
    }
}

@Composable
private fun ChipRow(
    title: String,
    items: List<String>,
    selected: String,
    accent: Color,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(top = HuiSpace.s)) {
        ZakoText(
            title,
            color = Color.White.huiTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = HuiSpace.screen, bottom = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = HuiSpace.screen),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items = items, key = { it }) { label ->
                val on = label == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
                        .clickable { onSelect(items.indexOf(label)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        color = if (on) accent else Color.White.huiSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 单个效果样例。
 *
 * 用一个 0→1 的进度驱动全部变换，缓动/时长直接来自用户选择 ——
 * 所以「换一条曲线」是真实生效的，不是换了个标题。
 */
@Composable
private fun EffectTile(
    type: HuiEffectType,
    easing: HuiEasing,
    durationMs: Int,
    accent: Color,
    index: Int
) {
    val progress = remember { Animatable(0f) }
    // 12 个样例同时跑无限动画，切到后台必须停，否则就是个常驻的 CPU 热源
    val started = com.huijui.sunext.edition.core.ui.component.rememberIsLifecycleStarted()

    LaunchedEffect(type, easing, durationMs, started) {
        if (HuiMotion.disabled() || !started) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            progress.snapTo(0f)
            kotlinx.coroutines.delay(index * 40L)
            progress.animateTo(1f, tween(HuiMotion.duration(durationMs), easing = easing.easing))
            kotlinx.coroutines.delay(500)
        }
    }

    val p = progress.value

    var modifier: Modifier = Modifier
        .size(96.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(accent.copy(alpha = 0.12f))

    modifier = when (type) {
        HuiEffectType.FADE -> modifier.graphicsLayer { alpha = p }
        HuiEffectType.SLIDE_UP -> modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 36f }
        HuiEffectType.SLIDE_DOWN -> modifier.graphicsLayer { alpha = p; translationY = -(1f - p) * 36f }
        HuiEffectType.SCALE_IN -> modifier.graphicsLayer { alpha = p; scaleX = 0.4f + 0.6f * p; scaleY = 0.4f + 0.6f * p }
        HuiEffectType.ROTATE_IN -> modifier.graphicsLayer { alpha = p; rotationZ = (1f - p) * -120f }
        HuiEffectType.SHIMMER -> modifier.huiShimmer(accent)
        HuiEffectType.GLOW -> modifier.huiGlow(accent)
        HuiEffectType.FLOAT -> modifier.graphicsLayer { translationY = kotlin.math.sin(p * 6.28f) * 10f }
        HuiEffectType.PULSE -> modifier.graphicsLayer { val s = 0.9f + 0.1f * p; scaleX = s; scaleY = s }
        HuiEffectType.GRADIENT_BORDER -> modifier.huiGradientBorder(listOf(accent, Color.White, accent))
        HuiEffectType.SPARKLE -> modifier.huiSweep(accent)
        HuiEffectType.SWEEP -> modifier.huiSweep(Color.White)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "${index + 1}",
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace
        )
    }
}
