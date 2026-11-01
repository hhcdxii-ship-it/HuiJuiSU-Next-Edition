package com.huijui.sunext.edition.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.design.HuiAlpha
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.HuiTopBar
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.language.HuiStrings
import com.huijui.sunext.edition.core.ui.language.LanguageMode
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 语言设置。
 *
 * 重构前的三个问题：
 *   1. 除了「简中 / 杂鱼」之外的 5 种语言**一律标成「施工中」且不可点** ——
 *      等于语言列表里有 5 行纯装饰。
 *   2. 选中判定用 `AppSettings.currentLanguage == mode.name`，而引导页写进去的是
 *      字符串 "NORMAL"（不是合法枚举），所以走完引导的用户进来**一个都不高亮**。
 *   3. 没有任何预览，用户点之前不知道会变成什么样。
 *
 * 现在：全部可选、实时预览、按覆盖率标注完成度，选择立即生效。
 */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color
    val current = HuiStrings.modeOf(AppSettings.currentLanguage)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = HuiSpace.s)) {
            HuiTopBar(title = "语言设置", subtitle = "LANGUAGE", onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = HuiSpace.screen),
            contentPadding = PaddingValues(top = HuiSpace.s, bottom = HuiSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(HuiSpace.s)
        ) {
            // 实时预览：用当前选中语言渲染同一句
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Translate,
                                null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(HuiSpace.s))
                            ZakoText(
                                "选择你的语言",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(HuiSpace.s))
                        ZakoText(
                            HuiStrings.preview(current),
                            color = accent,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            items(items = LanguageMode.entries, key = { it.name }) { mode ->
                val isSelected = mode == current
                val covered = HuiStrings.coverage(mode)
                val total = HuiStrings.totalKeys().coerceAtLeast(1)
                val ratio = (covered.toFloat() / total).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f)
                        )
                        .clickable {
                            AppSettings.currentLanguage = mode.name
                            AppSettings.save()
                            Toast.makeText(
                                context,
                                HuiStrings.tr("语言设置", mode) + " · " + mode.nativeName,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ZakoText(
                                mode.nativeName,
                                color = if (isSelected) accent else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.width(HuiSpace.s))
                            ZakoText(
                                mode.code,
                                color = Color.White.copy(alpha = HuiAlpha.TERTIARY),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        // 覆盖率条：直观看出一档语言到底完成了多少
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .height(3.dp)
                                    .background(if (isSelected) accent else Color.White.copy(alpha = 0.28f))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        ZakoText(
                            "$covered/$total",
                            color = Color.White.copy(alpha = 0.24f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item {
                ZakoText(
                    "杂鱼模式对未收录的文案会做语气兜底（100% 覆盖）；其余语言缺词时回落英文。",
                    color = Color.White.huiTertiary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = HuiSpace.s, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}
