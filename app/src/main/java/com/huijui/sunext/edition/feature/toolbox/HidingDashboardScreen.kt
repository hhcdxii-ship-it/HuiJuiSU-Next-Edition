package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.effect.HuiSkeletonList
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HuiHidingManager
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.layout.HuijuiLayout
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HidingDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<HuiHidingManager.HidingStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reload() {
        isLoading = true
        status = withTimeoutOrNull(8000L) { withContext(Dispatchers.IO) { HuiHidingManager.getStatus(context) } }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White)
            ZakoText("返回", color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        ZakoText("环境隔离防御", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
        ZakoText("HUI 专有护城河 · 纯净且克制", color = Color.White.copy(0.6f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading || status == null) {
            HuiSkeletonList(modifier = Modifier.fillMaxSize().padding(top = 8.dp))
        } else {
            val s = status!!
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFEF4444).copy(alpha = 0.1f)).border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gavel, null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            ZakoText("HUI 极客宣言", color = Color(0xFFEF4444), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ZakoText("Root 的初衷，是为了极致性能与深度美化，而不是去联机游戏里开挂破坏别人的体验。\n\n打不过就开挂急眼？那是小学生的把戏。HUI 永远站在纯粹极客这一边，绝不给任何网络游戏外挂提供生存土壤！", color = Color.White.copy(0.85f), fontSize = 13.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ZakoText(s.riskWarning ?: "", color = Color(0xFFEF4444).copy(0.8f), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ZakoText("当前防御等级", color = Color.White.copy(0.5f), fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!s.huiActive) {
                                ZakoText("未启用 (HUI 内核未接管)", color = Color(0xFFFFC107), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            } else if (s.allHidden) {
                                ZakoText("Level 3 · 内核级隔离", color = AppSettings.accentColor.color, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            } else {
                                ZakoText("部分暴露", color = Color(0xFFEF4444), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            // 这几行是按「本进程能否看见」判定的，不是按 HUI 是否激活
                            StatusRow("路径 /data/adb/huijui", s.pathVisible)
                            StatusRow("设备节点 /dev/huijui", s.deviceVisible)
                            StatusRow("挂载表痕迹", s.mountVisible)
                            StatusRow("内核模块名", s.kernelVisible)
                            Spacer(modifier = Modifier.height(8.dp))
                            ZakoText(
                                "以上从本应用(非 root)视角测量 —— 也就是其它普通应用能看到的范围",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch {
                            isLoading = true
                            val ok = withTimeoutOrNull(10000L) { withContext(Dispatchers.IO) { HuiHidingManager.clearMountsGlobal() } } ?: false
                            status = withTimeoutOrNull(8000L) { withContext(Dispatchers.IO) { HuiHidingManager.getStatus(context) } }
                            isLoading = false
                            Toast.makeText(context, if (ok) "已下发全局挂载擦除指令" else "执行失败或超时", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LayersClear, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                ZakoText("清理全局挂载痕迹", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                ZakoText("强制隐藏 HUI 模块的挂载点，防止被扫描", color = Color.White.copy(0.4f), fontSize = 11.sp)
                            }
                        }
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                ZakoText("已隔离应用 (${s.isolatedAppCount})", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                ZakoText("在超级用户中，为特定银行/检测App启用 Profile 隔离", color = Color.White.copy(0.4f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * @param visible true = 本进程能看见它 = 没藏住；null = 无法判定
 */
@Composable
private fun StatusRow(label: String, visible: Boolean?) {
    val (icon, tint, note) = when (visible) {
        false -> Triple(Icons.Default.CheckCircle, Color(0xFF81C784), "已隔离")
        true -> Triple(Icons.Default.Cancel, Color(0xFFEF4444), "暴露")
        null -> Triple(Icons.Default.HelpOutline, Color(0xFF9E9E9E), "未知")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZakoText(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZakoText(note, color = tint.copy(alpha = 0.85f), fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}
