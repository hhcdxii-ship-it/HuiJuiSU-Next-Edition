package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.language.ZakoText

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HardwareInfoProvider
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.layout.HuijuiLayout
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HardwareInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<HardwareInfoProvider.HardwareInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reload() {
        isLoading = true
        info = withContext(Dispatchers.IO) { HardwareInfoProvider.getHardwareInfo(context) }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal)) {
            Spacer(modifier = Modifier.height(HuijuiLayout.DetailTopSpacer))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                ZakoText("返回", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText("硬件信息", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    ZakoText("详细参数 · 一键复制", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.1f))
                        .clickable { scope.launch { reload() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, "刷新", tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading || info == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                }
            } else {
                val hw = info!!
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(HuijuiLayout.CardSpacingNormal),
                    contentPadding = HuijuiLayout.ScreenContentPadding
                ) {
                    item {
                        InfoSection(
                            icon = Icons.Default.PhoneAndroid,
                            title = "屏幕",
                            color = Color(0xFF64B5F6),
                            rows = listOf(
                                "分辨率" to hw.screenResolution,
                                "密度" to hw.screenDensity,
                                "刷新率" to hw.screenRefreshRate,
                                "尺寸" to hw.screenSizeInch,
                                "HDR" to hw.screenHdr
                            )
                        )
                    }

                    item {
                        InfoSection(
                            icon = Icons.Default.Speed,
                            title = "处理器",
                            color = Color(0xFFEF4444),
                            rows = listOf(
                                "型号" to hw.cpuModel,
                                "架构" to hw.cpuArchitecture,
                                "核心" to hw.cpuCores,
                                "频率" to hw.cpuFreqRange,
                                "ABI" to hw.cpuAbi
                            )
                        )
                    }

                    item {
                        InfoSection(
                            icon = Icons.Default.Memory,
                            title = "内存",
                            color = Color(0xFF81C784),
                            rows = listOf(
                                "总内存" to hw.ramTotal,
                                "可用内存" to hw.ramAvailable
                            )
                        )
                    }

                    item {
                        InfoSection(
                            icon = Icons.Default.SdStorage,
                            title = "存储",
                            color = Color(0xFFFFC107),
                            rows = listOf(
                                "总存储" to hw.storageTotal,
                                "可用存储" to hw.storageAvailable
                            )
                        )
                    }

                    item {
                        InfoSection(
                            icon = Icons.Default.Settings,
                            title = "系统",
                            color = Color(0xFFA855F7),
                            rows = listOf(
                                "Android" to hw.androidVersion,
                                "内核" to hw.kernelVersion,
                                "Build" to hw.buildNumber,
                                "安全补丁" to hw.securityPatch,
                                "指纹" to hw.fingerprint
                            )
                        )
                    }

                    item {
                        InfoSection(
                            icon = Icons.Default.Wifi,
                            title = "网络",
                            color = Color(0xFF0EA5B7),
                            rows = listOf("WiFi MAC" to hw.wifiMac)
                        )
                    }

                    item {
                        InfoSection(
                            icon = Icons.Default.BatteryFull,
                            title = "传感器",
                            color = Color(0xFFEC4899),
                            rows = listOf("数量" to hw.sensorCount)
                        )
                    }

                    // 复制按钮
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        CopyAllButton {
                            val text = HardwareInfoProvider.formatAsText(hw)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("HuiJuiSU 硬件信息", text))
                            Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("已复制全部信息到剪贴板"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    icon: ImageVector,
    title: String,
    color: Color,
    rows: List<Pair<String, String>>
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusSmall))
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                ZakoText(
                    title,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            rows.forEachIndexed { idx, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    ZakoText(
                        label,
                        color = Color.White.copy(0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.width(84.dp)
                    )
                    ZakoText(
                        value,
                        color = Color.White.copy(0.85f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (idx < rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(start = 84.dp)
                            .background(Color.White.copy(0.04f))
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyAllButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppSettings.accentColor.color.copy(alpha = 0.25f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ContentCopy,
                null,
                tint = AppSettings.accentColor.color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            ZakoText(
                "一键复制全部信息",
                color = AppSettings.accentColor.color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
