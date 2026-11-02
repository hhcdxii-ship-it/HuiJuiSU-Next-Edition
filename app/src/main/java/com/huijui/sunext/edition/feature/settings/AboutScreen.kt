package com.huijui.sunext.edition.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.BuildConfig
import com.huijui.sunext.edition.R
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 独立「关于」页。
 *
 * 之前是一个 AlertDialog：空间局促、放不下 logo 和说明，玻璃材质也体现不出来。
 * 现在改成独立页面：整屏液态玻璃卡、顶部品牌区、下面三条可点击入口。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HuiSpace.screen)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            ZakoText("返回", color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        // ── 品牌区 ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.5.dp, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_brand),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.height(14.dp))
            ZakoText("HuiJuiSU NEXT", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ZakoText(
                "v${BuildConfig.VERSION_NAME} · 纯血 LKM",
                color = Color.White.huiTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            ZakoText(
                "自研内核模块 · 模块热挂载 · 精细授权 · 环境伪装",
                color = Color.White.huiSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(26.dp))

        // ── 三个入口 ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AboutLink(
                    emoji = "🐙",
                    title = "GitHub 源码",
                    subtitle = "github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition",
                    accent = accent
                ) { open(context, "https://github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition") }

                AboutDivider()

                AboutLink(
                    emoji = "💚",
                    title = "酷安主页",
                    subtitle = "coolapk.com/u/43549687",
                    accent = accent
                ) { open(context, "https://www.coolapk.com/u/43549687") }

                AboutDivider()

                AboutLink(
                    emoji = "📦",
                    title = "内核模块下载",
                    subtitle = "GitHub Releases · 8 个 KMI 的 .ko",
                    accent = accent
                ) { open(context, "https://github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition/releases") }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── 说明 ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZakoText("说明", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                ZakoText(
                    "本应用需要 Root 才能发挥全部功能。刷机有风险，刷前请务必备份；" +
                        "内置模块按内核 KMI 分发，装错版本会被体检拦下而不是硬加载。",
                    color = Color.White.huiSecondary, fontSize = 11.sp, lineHeight = 17.sp
                )
                ZakoText(
                    "作弊哨兵只在「外挂 + 竞技游戏 + 真的在用 Root」同时成立时触发，" +
                        "且只销毁 HUI 自身环境，不会碰任何用户数据。",
                    color = Color.White.huiSecondary, fontSize = 11.sp, lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AboutDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent)
                )
            )
    )
}

@Composable
private fun AboutLink(
    emoji: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            ZakoText(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            ZakoText(subtitle, color = Color.White.huiTertiary, fontSize = 10.sp, maxLines = 1)
        }
        ZakoText("›", color = Color.White.copy(alpha = 0.30f), fontSize = 18.sp)
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}
