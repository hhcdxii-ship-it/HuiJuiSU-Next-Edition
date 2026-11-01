package com.huijui.sunext.edition.core.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 设计令牌。
 *
 * 重构前全项目散着 20/16/14/12/8dp 和 Color.White.copy(0.5f / 0.45f / 0.4f / 0.6f) 这类魔数，
 * 同一个「次级文字」在不同屏幕上是四个不同的灰度。这里统一收敛，改一处全局生效。
 */
object HuiSpace {
    val hair: Dp = 1.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    /** 屏幕左右安全边距。 */
    val screen: Dp = 20.dp

    /** 列表顶部 / 底部内容内边距（底部要给导航栏让位，由外层统一预留，这里只留呼吸）。 */
    val contentTop: Dp = 16.dp
    val contentBottom: Dp = 20.dp
}

object HuiAlpha {
    const val PRIMARY = 1.0f
    const val SECONDARY = 0.62f
    const val TERTIARY = 0.42f
    const val DISABLED = 0.24f
    const val HAIRLINE = 0.06f
    const val BORDER = 0.13f
}

/** 次级文字的统一入口，避免每个屏幕自己调 alpha。 */
val Color.huiSecondary: Color get() = copy(alpha = HuiAlpha.SECONDARY)
val Color.huiTertiary: Color get() = copy(alpha = HuiAlpha.TERTIARY)

/**
 * 统一顶栏。
 *
 * 各页面以前各写一套「返回箭头 + 28sp 粗体标题 + 副标题」，字号、间距、返回热区
 * 全都不一样。这里收成一个组件，高度和热区固定，改一处全局生效。
 */
@Composable
fun HuiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = HuiSpace.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = Color.White.huiSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(HuiSpace.xs))
        }
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                ZakoText(
                    subtitle,
                    color = Color.White.huiTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun HuiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                ZakoText(
                    subtitle,
                    color = Color.White.huiTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun HuiIconBadge(
    icon: ImageVector,
    tint: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    corner: Dp = 12.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 统一的加载态。替换各屏各写的 CircularProgressIndicator。 */
@Composable
fun HuiLoading(
    modifier: Modifier = Modifier,
    text: String = "正在读取...",
    compact: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 22.dp else 30.dp),
                strokeWidth = 2.dp,
                color = AppSettings.accentColor.color
            )
            if (!compact) {
                Spacer(Modifier.height(HuiSpace.m))
                ZakoText(text, color = Color.White.huiTertiary, fontSize = 12.sp)
            }
        }
    }
}

/** 统一的空态：图标 + 标题 + 说明 + 可选操作。 */
@Composable
fun HuiEmpty(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = HuiSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(HuiSpace.l))
        ZakoText(title, color = Color.White.huiSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(HuiSpace.xs))
            ZakoText(subtitle, color = Color.White.huiTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(HuiSpace.s))
            TextButton(onClick = onAction) {
                ZakoText(actionLabel, color = AppSettings.accentColor.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 统一的错误态：说人话 + 重试。 */
@Composable
fun HuiError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = HuiSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFFF6B6B).copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Warning,
                null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(HuiSpace.l))
        ZakoText(message, color = Color.White.huiSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(HuiSpace.s))
            TextButton(onClick = onRetry) {
                ZakoText("重试", color = AppSettings.accentColor.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 竖线分隔（列表行之间用，比 Divider 轻）。 */
@Composable
fun HuiHairline(modifier: Modifier = Modifier, startInset: Dp = 52.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(HuiSpace.hair)
            .background(Color.White.copy(alpha = HuiAlpha.HAIRLINE))
    )
}
