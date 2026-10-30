package com.huijui.sunext.edition.feature.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.BuildConfig
import com.huijui.sunext.edition.R
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppResetManager
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToTheme: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onResetComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ZakoText("设置", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            ZakoText("HuiJuiSU Next Edition", color = Color.White.copy(0.5f), fontSize = 12.sp)
        }

        item { SectionLabel("配置与个性化") }
        item { SettingsRow(Icons.Default.Palette, "主题设置", "自定义材质、壁纸与动画效果", onClick = onNavigateToTheme) }
        item { SettingsRow(Icons.Default.Language, "语言设置", "支持简繁、英日韩俄以及杂鱼语", onClick = onNavigateToLanguage) }

        item { SectionLabel("HUI 独有特性", modifier = Modifier.padding(top = 16.dp)) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    FeatureRow(Icons.Default.Hub, "Omni-Root 引擎", "兼容并接管 KSU/Magisk 底层生态")
                    FeatureRow(Icons.Default.Security, "3 级防格机拦截", "白名单分区过滤 + GKI 校验")
                    FeatureRow(Icons.Default.SettingsBackupRestore, "无感热备份", "刷机前自动 dd 备份，秒级回滚")
                    FeatureRow(Icons.Default.Language, "原生 WebUI 沙盒", "支持 Magisk/KSU 模块 HTML 控制台")
                }
            }
        }

        item { SectionLabel("重置与危险操作", modifier = Modifier.padding(top = 16.dp)) }
        item {
            SettingsRow(
                icon = Icons.Default.RestartAlt,
                title = "恢复出厂设置",
                subtitle = "清空所有主题、壁纸、引导状态（保留 Root 授权）",
                danger = true,
                onClick = { showResetDialog = true }
            )
        }

        item { SectionLabel("关于", modifier = Modifier.padding(top = 16.dp)) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(id = R.drawable.ic_zako), contentDescription = "Logo", modifier = Modifier.size(72.dp).clip(CircleShape))
                    Spacer(modifier = Modifier.height(12.dp))
                    ZakoText("HuiJuiSU NEXT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    ZakoText("Version ${BuildConfig.VERSION_NAME}", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { ZakoText("恢复出厂设置", color = Color.White) },
            text = { ZakoText("将清空所有主题、壁纸、引导状态，保留 Root 授权记录。此操作不可撤销，是否继续？", color = Color.White.copy(0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    isResetting = true
                    scope.launch {
                        val result = AppResetManager.resetToFactory(context)
                        isResetting = false
                        showResetDialog = false
                        if (result.success) { onResetComplete() }
                        else { Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show() }
                    }
                }) { ZakoText("确认重置", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { ZakoText("取消", color = Color.White.copy(0.6f)) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    ZakoText(text, color = Color.White.copy(0.5f), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val accent = if (danger) Color(0xFFEF4444) else AppSettings.accentColor.color

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(title, color = if (danger) Color(0xFFEF4444) else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                ZakoText(subtitle, color = Color.White.copy(0.5f), fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            ZakoText(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            ZakoText(subtitle, color = Color.White.copy(0.5f), fontSize = 11.sp)
        }
    }
}
