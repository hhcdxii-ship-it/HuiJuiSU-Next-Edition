package com.huijui.sunext.edition.feature.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.BatteryHealthProvider
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.effect.huiGlow
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 电池健康卡。
 *
 * `BatteryHealthProvider` 一直存在但全项目零引用 —— 它能读设计容量、
 * 实际满容量、循环次数、温度、电压，算出一个真实的健康度百分比。
 * 这些在普通系统设置里根本看不到，是 Root 管理器该有的东西。
 */
@Composable
fun BatteryHealthCard() {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color

    val health by produceState<BatteryHealthProvider.BatteryHealth?>(initialValue = null) {
        value = BatteryHealthProvider.getBatteryHealth(context)
    }

    val h = health
    val percent = h?.healthPercent ?: 0
    val tint = when {
        percent <= 0 -> accent
        percent >= 85 -> Color(0xFF81C784)
        percent >= 70 -> Color(0xFFFFC107)
        else -> Color(0xFFEF4444)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(tint.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .huiGlow(tint, 0.25f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (h?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(HuiSpace.m))
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText("电池健康", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    ZakoText(
                        if (h == null) "读取中…" else "${h.technology} · ${h.health}",
                        color = Color.White.huiTertiary,
                        fontSize = 11.sp
                    )
                }
                ZakoText(
                    if (percent > 0) "$percent%" else "--",
                    color = tint,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (h != null) {
                Spacer(Modifier.height(HuiSpace.m))
                Row(horizontalArrangement = Arrangement.spacedBy(HuiSpace.l)) {
                    BatteryMetric("设计容量", if (h.designCapacityMah > 0) "${h.designCapacityMah}mAh" else "--")
                    BatteryMetric("实际容量", if (h.actualCapacityMah > 0) "${h.actualCapacityMah}mAh" else "--")
                    BatteryMetric("循环次数", if (h.cycleCount > 0) "${h.cycleCount}" else "--")
                }
                Spacer(Modifier.height(HuiSpace.s))
                Row(horizontalArrangement = Arrangement.spacedBy(HuiSpace.l)) {
                    BatteryMetric("温度", if (h.temperature > 0) "${h.temperature}°C" else "--")
                    BatteryMetric("电压", if (h.voltage > 0) "${h.voltage}mV" else "--")
                    BatteryMetric("当前电量", if (h.level >= 0) "${h.level}%" else "--")
                }
            }
        }
    }
}

@Composable
private fun BatteryMetric(label: String, value: String) {
    Column {
        ZakoText(label, color = Color.White.huiTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        ZakoText(
            value,
            color = Color.White.huiSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
