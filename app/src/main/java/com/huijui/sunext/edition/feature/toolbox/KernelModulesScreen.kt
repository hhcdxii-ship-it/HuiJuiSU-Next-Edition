package com.huijui.sunext.edition.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.huijui.sunext.edition.core.root.KoMatrix
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.design.HuiAlpha
import com.huijui.sunext.edition.core.ui.design.HuiLoading
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 内核模块矩阵。
 *
 * 目的很直接：让「到底支持哪些内核」这件事**在界面上可查**。
 * 之前 8 个 .ko 全藏在 assets 里，用户唯一的反馈渠道是"刷完没效果"。
 */
@Composable
fun KernelModulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color

    val snapshot by produceState<KoMatrix.Snapshot?>(initialValue = null) {
        value = KoMatrix.snapshot(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 返回栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HuiSpace.s, vertical = HuiSpace.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, "返回", tint = Color.White.huiSecondary)
            }
            Spacer(Modifier.width(HuiSpace.xs))
            Column {
                ZakoText("内核模块", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
                ZakoText(
                    "KERNEL MODULES",
                    color = Color.White.huiTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        val snap = snapshot
        if (snap == null) {
            HuiLoading(modifier = Modifier.padding(top = 60.dp), text = "正在读取内置模块")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = HuiSpace.screen),
            contentPadding = PaddingValues(top = HuiSpace.s, bottom = HuiSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(HuiSpace.s)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val matched = snap.matchedEntry
                            Icon(
                                if (matched != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (matched != null) Color(0xFF81C784) else Color(0xFFFFC107)
                            )
                            Spacer(Modifier.width(HuiSpace.m))
                            Column(modifier = Modifier.weight(1f)) {
                                ZakoText(
                                    if (matched != null) "当前内核有匹配模块" else "当前内核没有匹配模块",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(2.dp))
                                ZakoText(
                                    snap.kernelRelease.ifBlank { "未知内核" },
                                    color = Color.White.huiSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(HuiSpace.m))
                        Row {
                            ZakoText("判定分支", color = Color.White.huiTertiary, fontSize = 11.sp)
                            Spacer(Modifier.width(HuiSpace.s))
                            ZakoText(
                                snap.currentBranch ?: "无",
                                color = if (snap.currentBranch != null) accent else Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            item {
                ZakoText(
                    "内置 ${snap.entries.size} 档（vermagic 为真实构建目标）",
                    color = Color.White.huiTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            items(items = snap.entries, key = { it.assetName }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (entry.matched) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Memory,
                        null,
                        tint = if (entry.matched) accent else Color.White.copy(alpha = 0.30f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(HuiSpace.m))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ZakoText(
                                entry.branch,
                                color = if (entry.matched) accent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (entry.matched) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(HuiSpace.s))
                            ZakoText(
                                "${entry.sizeBytes / 1024}KB",
                                color = Color.White.copy(alpha = HuiAlpha.TERTIARY),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        ZakoText(
                            entry.vermagic ?: "vermagic 读取失败",
                            color = Color.White.copy(alpha = 0.28f),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.matched) {
                        ZakoText("匹配", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                ZakoText(
                    "说明：内核只接受 KMI 分支一致的模块（5.10 / 5.15 / 6.1 / 6.6 / 6.12 / 6.18）。" +
                        "分支对不上时 insmod 会被拒绝，表现为「刷入成功但状态一直未工作」。",
                    color = Color.White.huiTertiary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = HuiSpace.s, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}
