package com.huijui.sunext.edition.feature.toolbox

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.*
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceInfo(
    val brand: String = "未知", val model: String = "未知",
    val androidVer: String = "未知", val kernelVer: String = "未知",
    val fingerprint: String = "未知", val selinux: String = "Unknown"
)

enum class ManagerState(val title: String, val color: Color) {
    NOT_WORKING("未工作", Color(0xFFEF4444)),
    WORKING_HUIJUI("独立", Color(0xFF81C784)),
    WORKING_ULTRA("兼容 (基于BakaSU)", Color(0xFF00E5FF)),
    WORKING_COMPAT("兼容", Color(0xFFFFC107))
}

@Composable
fun ToolboxScreen(
    onNavigateToFlash: () -> Unit,
    onNavigateToOverride: () -> Unit,
    onNavigateToModule: () -> Unit,
    onNavigateToHotInstall: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var managerState by remember { mutableStateOf(ManagerState.NOT_WORKING) }
    var detectedManager by remember { mutableStateOf<RootManager?>(null) }
    var managerTitle by remember { mutableStateOf("未获取权限") }
    var showTempRootDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) { currentTime = sdf.format(Date()); delay(10000) }
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        try {
            val result = withTimeoutOrNull(5000L) { // 🚀 加大超时时间
                withContext(Dispatchers.IO) {
                    try { HuijuiShell.exec("su -c id", useCache = false, timeoutMs = 2000L) } catch (_: Exception) {}
                    val detected = RootManagerDetector.detect(forceRefresh = false)
                    val isRooted = HuijuiShell.isRooted()
                    
                    val (bootState, chunqiu) = getBootAndChunqiu(context)

                    return@withContext Triple(detected, isRooted, Pair(bootState, chunqiu))
                }
            }

            if (result != null) {
                val (detected, isRooted, envCheck) = result
                val (bootState, chunqiu) = envCheck
                withContext(Dispatchers.Main) {
                    detectedManager = detected
                    deviceInfo = DeviceInfo(
                        brand = android.os.Build.MANUFACTURER ?: "未知",
                        model = android.os.Build.MODEL ?: "未知",
                        androidVer = android.os.Build.VERSION.RELEASE ?: "未知",
                        kernelVer = System.getProperty("os.version") ?: "未知",
                        fingerprint = android.os.Build.FINGERPRINT ?: "未知"
                    )
                    managerState = when {
                        !isRooted -> ManagerState.NOT_WORKING
                        detected == RootManager.HUIJUI -> ManagerState.WORKING_HUIJUI
                        detected == RootManager.HUIJUI_ULTRA -> ManagerState.WORKING_ULTRA
                        else -> ManagerState.WORKING_COMPAT
                    }
                    managerTitle = when (managerState) {
                        ManagerState.WORKING_HUIJUI -> "工作中 (HUI)"
                        ManagerState.WORKING_ULTRA -> "工作中 (HUI-Ultra)"
                        ManagerState.WORKING_COMPAT -> "工作中 (${detected?.shortName ?: "未知"} 兼容)"
                        ManagerState.NOT_WORKING -> "未工作"
                    }

                    if (!isRooted) {
                        val isTrulyCleanLocked =
                            bootState.isBootloaderLocked &&
                            !bootState.isProbablyRootedEnv &&
                            !bootState.isSuspicious &&
                            !chunqiu.isSuspicious &&
                            !chunqiu.isDefinitelyRooted &&
                            chunqiu.reasons.isEmpty()

                        if (isTrulyCleanLocked) {
                            showTempRootDialog = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppSettings.accentColor.color)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            ZakoText("SYSTEM TIME", color = Color.White.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            // 🚀 极致美学时钟：渐变色 + 发光阴影 + 适中字重
                            Text(
                                text = currentTime,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.White, Color(0xFFE0E0E0), Color.White.copy(alpha = 0.7f))
                                    ),
                                    fontSize = 76.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = AppSettings.accentColor.color.copy(alpha = 0.5f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 8f),
                                        blurRadius = 24f
                                    )
                                )
                            )
                        }
                        IconButton(onClick = {
                            if (!isLoading) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                refreshTrigger++
                            }
                        }) { Icon(Icons.Default.Refresh, "刷新", tint = Color.White.copy(0.5f)) }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (managerState == ManagerState.WORKING_HUIJUI) onNavigateToFlash() else onNavigateToOverride()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(56.dp).background(managerState.color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Shield, null, tint = managerState.color, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                ZakoText("ROOT STATUS", color = Color.White.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                ZakoText(managerTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                ZakoText(managerState.title, color = managerState.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${AppSettings.grantedApps.size}", color = managerState.color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (managerState == ManagerState.WORKING_HUIJUI) onNavigateToFlash() else onNavigateToOverride()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            ZakoText(if (managerState == ManagerState.WORKING_HUIJUI) "刷入镜像" else "底层重载", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onNavigateToModule) {
                        Column {
                            Icon(Icons.Default.Extension, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            ZakoText("模块挂载", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onNavigateToHotInstall) {
                        Column {
                            Icon(Icons.Default.Terminal, null, tint = Color(0xFFFF2A85), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            ZakoText("接管底层", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    var expanded by remember { mutableStateOf(false) }
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ZakoText("系统与设备信息", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.White.copy(0.5f))
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                DeviceInfoRow("设备型号", "${deviceInfo.brand} ${deviceInfo.model}")
                                DeviceInfoRow("系统版本", "Android ${deviceInfo.androidVer}")
                                DeviceInfoRow("内核版本", deviceInfo.kernelVer)
                                DeviceInfoRow("系统指纹", deviceInfo.fingerprint)
                            }
                        }
                    }
                }
            }
        }
    }

    // 🚀 幽灵模式弹窗
    if (showTempRootDialog) {
        var isExploiting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isExploiting) showTempRootDialog = false },
            title = { ZakoText("幽灵模式 👻", color = AppSettings.accentColor.color, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ZakoText(
                        text = if (isExploiting) "正在用幽灵模式打通内核，杂鱼别催啦..." else "检测到您的设备未解锁 Bootloader。是否要领取本小姐的「限时体验卡」，强行开启幽灵模式？\n（注：本模式基于漏洞利用，重启手机后即恢复原状哦~）",
                        color = Color.White.copy(0.8f)
                    )
                    if (isExploiting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = AppSettings.accentColor.color)
                    }
                }
            },
            containerColor = Color(0xFF2C2C2C),
            confirmButton = {
                if (!isExploiting) {
                    TextButton(onClick = {
                        isExploiting = true
                        scope.launch {
                            val success = ExploitManager.runGhostMode(context) { log ->
                                android.util.Log.d("GhostMode", log)
                            }
                            withContext(Dispatchers.Main) {
                                isExploiting = false
                                showTempRootDialog = false
                                if (success) {
                                    Toast.makeText(context, "幽灵模式已开启！重启后失效哦！", Toast.LENGTH_LONG).show()
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(context, "漏洞执行失败，请查看日志或联系开发者。", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) {
                        ZakoText("领取体验卡", color = AppSettings.accentColor.color, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isExploiting) {
                    TextButton(onClick = { showTempRootDialog = false }) {
                        ZakoText("怂了", color = Color.Gray)
                    }
                }
            }
        )
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ZakoText(label, color = Color.White.copy(0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        ZakoText(value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
    }
}

// 简单的内存缓存
private var lastBootState: BootloaderDetector.BootState? = null
private var lastChunqiuResult: ChunqiuDetector.DetectionResult? = null
private var lastCheckTime = 0L
private const val CHECK_CACHE_TTL = 30_000L // 30秒内不重复检测

suspend fun getBootAndChunqiu(context: android.content.Context): Pair<BootloaderDetector.BootState, ChunqiuDetector.DetectionResult> {
    val now = System.currentTimeMillis()
    if (lastBootState != null && lastChunqiuResult != null && now - lastCheckTime < CHECK_CACHE_TTL) {
        return Pair(lastBootState!!, lastChunqiuResult!!)
    }
    val boot = BootloaderDetector.detectBootState()
    val chunqiu = ChunqiuDetector.comprehensiveCheck(context)
    lastBootState = boot
    lastChunqiuResult = chunqiu
    lastCheckTime = now
    return Pair(boot, chunqiu)
}
