package com.huijui.sunext.edition.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.HuiStrings
import com.huijui.sunext.edition.core.ui.language.LanguageMode
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.language.zakoString
import com.huijui.sunext.edition.core.utils.AppSettings

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                ZakoText("语言设置", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                ZakoText("Language Settings", color = Color.White.copy(0.6f), fontSize = 12.sp)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(LanguageMode.values()) { mode ->
                val isSelected = AppSettings.currentLanguage == mode.name
                // 🚀 施工中判定：只有简中和杂鱼能用
                val isUnderConstruction = mode != LanguageMode.ZH_CN && mode != LanguageMode.ZAKO

                LanguageCard(
                    mode = mode,
                    isSelected = isSelected,
                    isUnderConstruction = isUnderConstruction,
                    onClick = {
                        if (isUnderConstruction) {
                            Toast.makeText(context, "这个语言还在施工中哦，笨蛋！", Toast.LENGTH_SHORT).show()
                        } else {
                            AppSettings.currentLanguage = mode.name
                            AppSettings.save()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(
    mode: LanguageMode,
    isSelected: Boolean,
    isUnderConstruction: Boolean,
    onClick: () -> Unit
) {
    val accent = AppSettings.accentColor.color
    val borderColor = if (isSelected) accent else Color.White.copy(0.08f)
    
    // 🚀 颜色控制：选中为主题色，施工中为半透明灰，普通为白
    val textColor = when {
        isSelected -> accent
        isUnderConstruction -> Color.White.copy(0.3f)
        else -> Color.White
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZakoText(
                        text = mode.displayName,
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    // 🚀 施工中标签
                    if (isUnderConstruction) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "施工中",
                                color = Color(0xFFFF9800),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                ZakoText(
                    text = mode.code,
                    color = if (isUnderConstruction) Color.White.copy(0.2f) else Color.White.copy(0.4f),
                    fontSize = 11.sp
                )
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
