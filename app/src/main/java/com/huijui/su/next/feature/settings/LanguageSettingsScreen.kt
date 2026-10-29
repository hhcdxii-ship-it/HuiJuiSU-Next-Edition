package com.huijui.su.next.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.language.LanguageMode
import com.huijui.su.next.core.ui.layout.HuijuiLayout
import com.huijui.su.next.core.utils.AppSettings

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal),
        contentPadding = PaddingValues(top = HuijuiLayout.DetailTopSpacer, bottom = HuijuiLayout.ScreenBottom),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                Text("返回", color = Color.White, fontSize = 16.sp)
            }
        }

        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("语言设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Language Settings", color = Color.White.copy(0.6f), fontSize = 12.sp)
            }
        }

        items(LanguageMode.values()) { mode ->
            val isSelected = AppSettings.currentLanguage == mode.name
            LanguageCard(
                mode = mode,
                isSelected = isSelected,
                onClick = {
                    AppSettings.currentLanguage = mode.name
                    AppSettings.save()
                }
            )
        }
    }
}

@Composable
private fun LanguageCard(
    mode: LanguageMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = AppSettings.accentColor.color
    val borderColor = if (isSelected) accent else Color.White.copy(0.08f)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) accent else Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = mode.code,
                    color = Color.White.copy(0.4f),
                    fontSize = 11.sp
                )
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
