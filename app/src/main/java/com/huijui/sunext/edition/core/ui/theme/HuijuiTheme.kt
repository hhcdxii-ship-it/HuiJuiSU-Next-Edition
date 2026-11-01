package com.huijui.sunext.edition.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.MaterialStyle

/**
 * 玻璃材质的层级色板。
 *
 * 为什么不是一堆写死的 Color.White.copy(0.08f)：
 *   1. 卡片要靠「层级色差」立起来，而不是靠描边 —— fill 分顶面高光 / 主体 / 底部压暗三层。
 *   2. 主色（accent）要能渗进玻璃，否则换主题色时卡片还是灰的。
 *   3. SOLID / NONE 由 [MaterialStyle] 驱动，设置页那三个开关必须真的生效。
 */
data class HuiGlassPalette(
    val fillTop: Color,
    val fillBottom: Color,
    val sheen: Color,
    val border: Color,
    val solidContainer: Color,
    val solidBorder: Color,
)

val LocalHuiGlassPalette = staticCompositionLocalOf {
    HuiGlassPalette(
        fillTop = Color.White.copy(alpha = 0.11f),
        fillBottom = Color.White.copy(alpha = 0.045f),
        sheen = Color.White.copy(alpha = 0.16f),
        border = Color.White.copy(alpha = 0.15f),
        solidContainer = Color(0xFF1B1B1F),
        solidBorder = Color.White.copy(alpha = 0.08f),
    )
}

object HuiTheme {
    val glass: HuiGlassPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalHuiGlassPalette.current
}

/**
 * 全局主题。
 *
 * 原实现「彻底脱离 MaterialTheme」，副作用是 AlertDialog / CircularProgressIndicator /
 * TextButton 全退回 M3 默认的浅色紫主题，在纯黑界面上非常突兀。
 * 现在改成：Material You 结构 + 用户选的 accent 作为 primary，深色为唯一基准。
 */
@Composable
fun HuijuiTheme(content: @Composable () -> Unit) {
    val accent = AppSettings.accentColor.color
    val style = AppSettings.materialStyle

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF07110A),
        primaryContainer = accent.copy(alpha = 0.22f),
        onPrimaryContainer = accent,
        secondary = accent.copy(alpha = 0.78f),
        onSecondary = Color.Black,
        tertiary = accent.copy(alpha = 0.60f),
        background = Color(0xFF0A0A0C),
        onBackground = Color(0xFFF3F3F6),
        surface = Color(0xFF121215),
        onSurface = Color(0xFFF3F3F6),
        surfaceVariant = Color(0xFF1C1C21),
        onSurfaceVariant = Color(0xFFA9A9B4),
        outline = Color.White.copy(alpha = 0.16f),
        outlineVariant = Color.White.copy(alpha = 0.08f),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF2A0505),
        scrim = Color.Black,
        inverseSurface = Color(0xFFF3F3F6),
        inverseOnSurface = Color(0xFF121215),
    )

    CompositionLocalProvider(LocalHuiGlassPalette provides paletteFor(accent, style)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun paletteFor(accent: Color, style: MaterialStyle): HuiGlassPalette = when (style) {
    MaterialStyle.GLASS -> HuiGlassPalette(
        fillTop = Color.White.copy(alpha = 0.115f),
        fillBottom = Color.White.copy(alpha = 0.045f).compositeOver(accent.copy(alpha = 0.06f)),
        sheen = Color.White.copy(alpha = 0.16f),
        border = Color.White.copy(alpha = 0.15f),
        solidContainer = Color(0xFF1B1B1F),
        solidBorder = Color.White.copy(alpha = 0.08f),
    )
    MaterialStyle.SOLID -> HuiGlassPalette(
        fillTop = Color(0xFF1E1E23),
        fillBottom = Color(0xFF15151A),
        sheen = Color.Transparent,
        border = Color.White.copy(alpha = 0.07f),
        solidContainer = Color(0xFF1E1E23),
        solidBorder = Color.White.copy(alpha = 0.07f),
    )
    MaterialStyle.NONE -> HuiGlassPalette(
        fillTop = Color.Transparent,
        fillBottom = Color.Transparent,
        sheen = Color.Transparent,
        border = Color.Transparent,
        solidContainer = Color.Transparent,
        solidBorder = Color.Transparent,
    )
}
