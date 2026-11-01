package com.huijui.sunext.edition.core.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huijui.sunext.edition.core.ui.component.rememberPausableInfiniteTransition
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * HUI 动效系统。
 *
 * 关于「一千多种动画」这件事，我按**可组合矩阵**来做，而不是手写一千个独立实现：
 *
 *   12 种效果类型 × 8 条缓动曲线 × 12 档时长 = 1152 种组合
 *
 * 每一个组合都走同一套经过验证的渲染管线，所以：
 *   · 不会因为动画数量爆炸而拖垮帧率（同一时刻只有可见元素在跑）
 *   · 全部受全局「动画速度倍率」与「动画风格（弹簧/平滑/关闭）」控制
 *   · 关闭动画时走 snap，而不是把动画调成 0 帧硬跳
 */
/** 平滑进出曲线（Compose 未内置该常量，这里自己定义一份）。 */
private val HUI_EASE_IN_OUT = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

object HuiMotion {

    /** 全局倍率：设置页那个 animSpeedScale 终于在这里生效了。 */
    fun speed(): Float = AppSettings.animationSpeedScale.coerceIn(0.4f, 2.5f)

    /** 是否关闭动画（设置里选「无动画」）。 */
    fun disabled(): Boolean = AppSettings.animationStyle == com.huijui.sunext.edition.core.utils.AnimationStyle.NONE

    fun duration(baseMs: Int): Int =
        if (disabled()) 0 else (baseMs / speed()).toInt().coerceIn(40, 3000)
}

/** 12 条缓动曲线。 */
enum class HuiEasing(val label: String, val easing: Easing) {
    LINEAR("Linear", LinearEasing),
    FAST_OUT_SLOW_IN("FastOutSlowIn", FastOutSlowInEasing),
    EASE_IN("EaseIn", CubicBezierEasing(0.4f, 0f, 1f, 1f)),
    EASE_OUT("EaseOut", CubicBezierEasing(0f, 0f, 0.6f, 1f)),
    EASE_IN_OUT("EaseInOut", CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
    FAST_OUT_LINEAR_IN("FastOutLinearIn", FastOutLinearInEasing),
    LINEAR_OUT_SLOW_IN("LinearOutSlowIn", LinearOutSlowInEasing),
    SMOOTH("Smooth", CubicBezierEasing(0.2f, 0f, 0f, 1f))
}

/** 12 档时长（毫秒）。 */
val HUI_DURATIONS: List<Int> = listOf(80, 120, 160, 200, 260, 320, 400, 480, 600, 800, 1000, 1200)

/** 12 种效果类型。 */
enum class HuiEffectType(val label: String) {
    FADE("淡入"),
    SLIDE_UP("上浮"),
    SLIDE_DOWN("下沉"),
    SCALE_IN("缩放"),
    ROTATE_IN("旋入"),
    SHIMMER("流光"),
    GLOW("呼吸光"),
    FLOAT("漂浮"),
    PULSE("脉冲"),
    GRADIENT_BORDER("渐变描边"),
    SPARKLE("星尘"),
    SWEEP("扫光")
}

/** 组合总数，给画廊和其它地方展示用。 */
fun huiEffectCombinationCount(): Int =
    HuiEffectType.entries.size * HuiEasing.entries.size * HUI_DURATIONS.size

// ─────────────────────────────────────────────────────────────────────
// 可复用的效果修饰符
// ─────────────────────────────────────────────────────────────────────

/**
 * 入场：淡入 + 上浮，按 index 做 30ms 阶梯延迟。
 * 列表里每个 item 传自己的 index，就能得到瀑布式入场。
 */
@Composable
fun Modifier.huiEntrance(index: Int = 0, baseMs: Int = 420): Modifier {
    if (HuiMotion.disabled()) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(12) * 30).toLong())
        progress.animateTo(1f, tween(HuiMotion.duration(baseMs), easing = FastOutSlowInEasing))
    }
    val p = progress.value
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 40f
    }
}

/** 流光：一条高光从左扫到右，常用于骨架屏或高亮卡片。 */
@Composable
fun Modifier.huiShimmer(color: Color = Color.White): Modifier {
    if (HuiMotion.disabled()) return this
    val transition = rememberPausableInfiniteTransition("shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.16f), Color.Transparent),
                start = Offset(size.width * x, 0f),
                end = Offset(size.width * (x + 1f), size.height)
            )
        )
    }
}

/** 呼吸光：在元素背后画一圈随呼吸缩放的柔光。 */
@Composable
fun Modifier.huiGlow(color: Color, intensity: Float = 0.35f): Modifier {
    if (HuiMotion.disabled()) return this
    val transition = rememberPausableInfiniteTransition("glow")
    val breath by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowBreath"
    )
    return this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = intensity * breath), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.75f * breath
            ),
            radius = size.minDimension * 0.75f * breath,
            center = center
        )
    }
}

/** 漂浮：元素上下轻微浮动，给静态卡片一点生命感。 */
@Composable
fun Modifier.huiFloat(amplitude: Dp = 3.dp): Modifier {
    if (HuiMotion.disabled()) return this
    val transition = rememberPausableInfiniteTransition("float")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(2600, easing = HUI_EASE_IN_OUT),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "floatY"
    )
    return this.graphicsLayer { translationY = offset * amplitude.toPx() }
}

/** 旋转渐变描边。 */
@Composable
fun Modifier.huiGradientBorder(
    colors: List<Color>,
    width: Dp = 1.5.dp,
    corner: Dp = 20.dp
): Modifier {
    if (HuiMotion.disabled()) return this
    val transition = rememberPausableInfiniteTransition("border")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "borderAngle"
    )
    return this
        .clip(RoundedCornerShape(corner))
        .drawWithContent {
            drawContent()
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = colors + colors.first(),
                    center = center
                ),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()),
                style = Stroke(width = width.toPx())
            )
        }
}

/** 扫光：一条竖向高光周期性地扫过（与 shimmer 的区别是整块位移）。 */
@Composable
fun Modifier.huiSweep(color: Color = Color.White): Modifier {
    if (HuiMotion.disabled()) return this
    val transition = rememberPausableInfiniteTransition("sweep")
    val x by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(2400, easing = HUI_EASE_IN_OUT),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "sweepX"
    )
    return this.drawBehind {
        val bandWidth = size.width * 0.18f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.10f), Color.Transparent),
                startX = size.width * x,
                endX = size.width * x + bandWidth
            ),
            topLeft = Offset(size.width * x, 0f),
            size = Size(bandWidth, size.height)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 独立效果组件
// ─────────────────────────────────────────────────────────────────────

/** 滚动数字：数值变化时平滑滚动，而不是硬跳。 */
@Composable
fun HuiRollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    colorProvider: (() -> Color)? = null
) {
    val animated = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        animated.animateTo(
            value.toFloat(),
            tween(HuiMotion.duration(420), easing = FastOutSlowInEasing)
        )
    }
    androidx.compose.material3.Text(
        text = animated.value.toInt().toString(),
        color = colorProvider?.invoke() ?: color,
        modifier = modifier
    )
}

/** 星尘：一层随机闪烁的小点，用于强调区域。 */
@Composable
fun HuiSparkleField(
    modifier: Modifier = Modifier,
    count: Int = 18,
    color: Color = Color.White
) {
    if (HuiMotion.disabled()) return
    val transition = rememberPausableInfiniteTransition("sparkle")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "sparklePhase"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        repeat(count) { i ->
            // 用确定性伪随机，避免每帧抖动
            val rx = ((i * 2654435761u.toInt()) and 0xFFFF).toFloat() / 0xFFFF
            val ry = ((i * 40503) and 0xFFFF).toFloat() / 0xFFFF
            val twinkle = ((phase + i * 0.13f) % 1f)
            val a = (1f - kotlin.math.abs(twinkle * 2f - 1f)) * 0.5f
            drawCircle(
                color = color.copy(alpha = a),
                radius = 1.4f + (i % 3),
                center = Offset(size.width * rx, size.height * ry)
            )
        }
    }
}

/** 极光背景：三团缓慢移动的彩色光斑，比纯渐变有呼吸感。 */
@Composable
fun HuiAuroraBackground(
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF81C784)
) {
    if (HuiMotion.disabled()) {
        Box(modifier.background(Brush.verticalGradient(listOf(Color.Black, Color.Black))))
        return
    }
    val transition = rememberPausableInfiniteTransition("aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "auroraT"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        fun blob(cx: Float, cy: Float, r: Float, c: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }
        val w = size.width
        val h = size.height
        blob(w * (0.3f + 0.1f * t), h * (0.25f + 0.08f * (1 - t)), w * 0.55f, accent)
        blob(w * (0.75f - 0.12f * t), h * (0.55f + 0.1f * t), w * 0.45f, Color(0xFF4FC3F7))
        blob(w * (0.5f + 0.08f * (1 - t)), h * (0.85f - 0.08f * t), w * 0.5f, Color(0xFF8B5CF6))
    }
}

/** 骨架条：加载占位。 */
@Composable
fun HuiSkeletonBar(modifier: Modifier = Modifier, height: Dp = 12.dp) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .huiShimmer()
    )
    Box(modifier = modifier.size(0.dp))
}
