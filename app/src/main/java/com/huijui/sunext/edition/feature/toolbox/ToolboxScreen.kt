package com.huijui.sunext.edition.feature.toolbox

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.*
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.component.rememberIsLifecycleStarted
import com.huijui.sunext.edition.core.ui.component.rememberPausableInfiniteTransition
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceInfo(
    val brand: String = "未知", val model: String = "未知",
    val androidVer: String = "未知", val kernelVer: String = "未知",
    val fingerprint: String = "未知", val selinux: String = "Unknown"
)

/**
 * 主页。
 *
 * 相比旧版的改动（每一条都是能看见的问题）：
 *   1. **不再有全屏转圈**。旧版 `isLoading` 一置 true 就把整页换成 CircularProgressIndicator，
 *      点一下刷新按钮整页闪一下白 —— 现在只在卡片角上转一个小圈，内容永远在场。
 *   2. **状态卡是唯一视觉焦点**。旧版有三张同样权重的全宽卡（状态卡 + 刷入卡 + 重载卡），
 *      用户根本不知道该看哪。现在改成「一张 hero 卡 + 2×2 快捷入口」。
 *   3. **状态切换有连续过渡**。旧版直接 `managerTitle = ...` 硬切；现在底色、光晕、
 *      文案共用同一条颜色插值链，dot 在工作状态下才呼吸。
 *   4. **状态不再三重探测**。旧版一次刷新里既跑 RootManagerDetector 又跑 isRooted()，
 *      外层还套 5s 超时（经常超时后显示旧状态）。现在统一走 RootStatusRepository。
 *   5. 修掉了状态栏 padding 加两次导致顶部空白过高、以及底部 100dp + 120dp 双重留白。
 */
@Composable
fun ToolboxScreen(
    onNavigateToFlash: () -> Unit,
    onNavigateToOverride: () -> Unit,
    onNavigateToModule: () -> Unit,
    onNavigateToHotInstall: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val status by RootStatusRepository.status.collectAsState()
    val isRefreshing by RootStatusRepository.isRefreshing.collectAsState()

    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var currentTime by remember { mutableStateOf("") }
    var showTempRootDialog by remember { mutableStateOf(false) }
    var ghostChecked by remember { mutableStateOf(false) }

    // ── 时钟：只在分钟变化时重组，且切后台立刻停 ────────────────────
    //    之前是无条件 5 秒醒一次 —— 后台也照跑，等于一个常量唤醒源。
    val clockStarted = rememberIsLifecycleStarted()
    LaunchedEffect(clockStarted) {
        if (!clockStarted) return@LaunchedEffect
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        var last = ""
        while (true) {
            val now = sdf.format(Date())
            if (now != last) {
                last = now
                currentTime = now
            }
            delay(5_000)
        }
    }

    // ── 设备信息：一次 IO，别在每次刷新里重建 ──────────────────────────
    LaunchedEffect(Unit) {
        deviceInfo = withContext(Dispatchers.IO) {
            // DeviceInfoProvider 把 6 个字段合并成 1 条 shell 命令并做 5 秒缓存，
            // 比逐个 getprop/Build.* 更准（部分 ROM 的 Build 字段与 getprop 不一致），
            // 也避免反复刷新时重复跑命令。它本来就是为这块写的，只是没接上。
            val info = runCatching { DeviceInfoProvider.get() }.getOrNull()
            if (info != null) {
                DeviceInfo(
                    brand = info.brand,
                    model = info.model,
                    androidVer = info.androidVer,
                    kernelVer = info.kernelVer,
                    fingerprint = info.fingerprint,
                    selinux = info.selinux
                )
            } else {
                DeviceInfo(
                    brand = Build.MANUFACTURER ?: "未知",
                    model = Build.MODEL ?: "未知",
                    androidVer = Build.VERSION.RELEASE ?: "未知",
                    kernelVer = System.getProperty("os.version") ?: "未知",
                    fingerprint = Build.FINGERPRINT ?: "未知"
                )
            }
        }
    }

    // ── 状态轮询：只在前台跑。切后台立刻停，回来立刻补一次 ────────────
    val started = rememberIsLifecycleStarted()
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        RootStatusRepository.refresh(force = true)
        while (true) {
            delay(5_000)
            RootStatusRepository.refresh()
        }
    }

    // ── 幽灵模式：只在「确实没 Root」且还没问过的时候探一次环境 ────────
    LaunchedEffect(status) {
        if (status is HuiRootStatus.NotRooted && !ghostChecked) {
            ghostChecked = true
            val clean = withContext(Dispatchers.IO) {
                runCatching {
                    val (boot, chunqiu) = getBootAndChunqiu(context)
                    boot.isBootloaderLocked &&
                        !boot.isProbablyRootedEnv &&
                        !boot.isSuspicious &&
                        !chunqiu.isSuspicious &&
                        !chunqiu.isDefinitelyRooted &&
                        chunqiu.reasons.isEmpty()
                }.getOrDefault(false)
            }
            if (clean) showTempRootDialog = true
        }
    }

    val isWorking = status.isWorking
    val accent = AppSettings.accentColor.color

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                HeaderRow(
                    currentTime = currentTime,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { RootStatusRepository.refresh(force = true) }
                    }
                )
            }

            item(span = { GridItemSpan(2) }) {
                HeroStatusCard(
                    status = status,
                    grantedCount = AppSettings.grantedApps.size,
                    onClick = { if (isWorking) onNavigateToFlash() else onNavigateToOverride() }
                )
            }

            item {
                QuickActionCard(
                    icon = Icons.Default.SystemUpdate,
                    title = if (isWorking) "刷入镜像" else "底层重载",
                    tint = accent,
                    onClick = if (isWorking) onNavigateToFlash else onNavigateToOverride
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.Default.Terminal,
                    title = "接管底层",
                    tint = Color(0xFFFF2A85),
                    onClick = onNavigateToHotInstall
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.Default.Extension,
                    title = "模块挂载",
                    tint = Color(0xFF8B5CF6),
                    onClick = onNavigateToModule
                )
            }
            item {
                QuickActionCard(
                    icon = Icons.Default.Memory,
                    title = "内核接管",
                    tint = Color(0xFF4FC3F7),
                    onClick = onNavigateToOverride
                )
            }

            item(span = { GridItemSpan(2) }) {
                DeviceInfoCard(deviceInfo)
            }
        }
    }

    if (showTempRootDialog) {
        var isExploiting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isExploiting) showTempRootDialog = false },
            title = { ZakoText("幽灵模式 👻", color = accent, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ZakoText(
                        text = if (isExploiting) "正在用幽灵模式打通内核，杂鱼别催啦..." else "检测到您的设备未解锁 Bootloader。是否要领取本小姐的「限时体验卡」，强行开启幽灵模式？\n（注：本模式基于漏洞利用，重启手机后即恢复原状哦~）",
                        color = Color.White.copy(0.8f)
                    )
                    if (isExploiting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = accent
                        )
                    }
                }
            },
            containerColor = Color(0xFF1C1C21),
            confirmButton = {
                if (!isExploiting) {
                    TextButton(onClick = {
                        isExploiting = true
                        scope.launch {
                            val success = ExploitManager.runGhostMode(context) { log ->
                                android.util.Log.d("GhostMode", log)
                            }
                            isExploiting = false
                            showTempRootDialog = false
                            if (success) {
                                Toast.makeText(context, "幽灵模式已开启！重启后失效哦！", Toast.LENGTH_LONG).show()
                                RootStatusRepository.invalidate()
                                RootStatusRepository.refresh(force = true)
                            } else {
                                Toast.makeText(context, "漏洞执行失败，请查看日志或联系开发者。", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        ZakoText("领取体验卡", color = accent, fontWeight = FontWeight.Bold)
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

// ─────────────────────────────────────────────────────────────────────
// 头部：时钟 + 刷新
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun HeaderRow(
    currentTime: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            ZakoText(
                "SYSTEM TIME",
                color = Color.White.copy(0.45f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentTime.ifEmpty { "--:--" },
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color(0xFFE2E2E6), Color.White.copy(alpha = 0.65f))
                    ),
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    shadow = Shadow(
                        color = AppSettings.accentColor.color.copy(alpha = 0.45f),
                        offset = Offset(0f, 8f),
                        blurRadius = 22f
                    )
                )
            )
        }

        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = AppSettings.accentColor.color
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = Color.White.copy(0.55f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Hero 状态卡
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun HeroStatusCard(
    status: HuiRootStatus,
    grantedCount: Int,
    onClick: () -> Unit
) {
    val target = statusColor(status)
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "heroStatusColor"
    )
    val title = statusTitle(status)
    val isWorking = status.isWorking

    val transition = rememberPausableInfiniteTransition("heroPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroPulse"
    )
    val breath = if (isWorking) pulse else 0f

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 右上角光晕：状态色的"呼吸"，是整张卡最贵的一笔质感
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = size.maxDimension * 0.92f
                val center = Offset(size.width * 0.86f, size.height * 0.10f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.10f + 0.18f * breath),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(color.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = color, modifier = Modifier.size(26.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(color = color, breath = breath, active = isWorking)
                        Spacer(modifier = Modifier.width(6.dp))
                        ZakoText(
                            "ROOT STATUS",
                            color = Color.White.copy(0.45f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (fadeIn(tween(260)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(140))
                        },
                        label = "heroTitle"
                    ) { text ->
                        ZakoText(
                            text,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    ZakoText(
                        subtitled(status),
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    ZakoText("授权", color = Color.White.copy(0.4f), fontSize = 10.sp)
                    Text(
                        text = grantedCount.toString(),
                        color = color,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, breath: Float, active: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = 0.72f + breath * 0.46f
                        scaleY = 0.72f + breath * 0.46f
                        alpha = 0.34f - breath * 0.22f
                    }
                    .background(color, CircleShape)
            )
        }
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
    }
}

// ─────────────────────────────────────────────────────────────────────
// 快捷入口
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Box(
                modifier = Modifier.size(38.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            ZakoText(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 设备信息
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun DeviceInfoCard(deviceInfo: DeviceInfo) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "deviceArrow"
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ZakoText(
                    "系统与设备信息",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    null,
                    tint = Color.White.copy(0.5f),
                    modifier = Modifier.rotate(arrow)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(140))
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    DeviceInfoRow("设备型号", "${deviceInfo.brand} ${deviceInfo.model}")
                    DeviceInfoRow("系统版本", "Android ${deviceInfo.androidVer}")
                    // osMajorVersion / isHyperOs 这两个字段之前只写不读，设备信息里正好展示出来
                    if (AppSettings.osMajorVersion.isNotBlank() && AppSettings.osMajorVersion != "Android") {
                        DeviceInfoRow(
                            "ROM",
                            AppSettings.osMajorVersion + if (AppSettings.isXiaomiDevice) " (小米系)" else ""
                        )
                    }
                    DeviceInfoRow("内核版本", deviceInfo.kernelVer)
                    DeviceInfoRow("SELinux", deviceInfo.selinux)
                    DeviceInfoRow("系统指纹", deviceInfo.fingerprint)
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ZakoText(label, color = Color.White.copy(0.45f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        ZakoText(
            value,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 状态映射
// ─────────────────────────────────────────────────────────────────────

private fun statusColor(status: HuiRootStatus): Color = when (status) {
    HuiRootStatus.Unknown -> Color(0xFF8E8E96)
    HuiRootStatus.NotRooted -> Color(0xFFEF4444)
    is HuiRootStatus.Locked -> Color(0xFFFFC107)
    is HuiRootStatus.Working -> when (status.engine) {
        HuiEngine.HUI_NATIVE -> Color(0xFF81C784)   // 纯血：绿
        HuiEngine.HUI_ALONGSIDE -> Color(0xFF00E5FF) // 叠加：青
        HuiEngine.HUI_STALE -> Color(0xFFFFC107)     // 残留：琥珀
        HuiEngine.BORROWED -> Color(0xFF4FC3F7)      // 借用：蓝
    }
}

private fun statusTitle(status: HuiRootStatus): String = when (status) {
    HuiRootStatus.Unknown -> "检测中..."
    HuiRootStatus.NotRooted -> "未工作"
    is HuiRootStatus.Locked -> "已锁定"
    is HuiRootStatus.Working -> when (status.engine) {
        HuiEngine.HUI_NATIVE -> "工作中 (HUI)"
        HuiEngine.HUI_ALONGSIDE -> "工作中 (HUI · ${managerLabel(status.manager)})"
        HuiEngine.HUI_STALE -> "引擎未响应"
        HuiEngine.BORROWED -> "兼容模式 (${managerLabel(status.manager)})"
    }
}

/** detector 认不出来时不要显示 "None" / "Unknown"，给一个人话标签。 */
private fun managerLabel(manager: RootManager): String = when (manager) {
    RootManager.NONE, RootManager.UNKNOWN -> "通用 su"
    else -> manager.shortName
}

private fun subtitled(status: HuiRootStatus): String = when (status) {
    HuiRootStatus.Unknown -> "正在探测底层引擎"
    HuiRootStatus.NotRooted -> "引擎未唤醒 · 点此重新接管"
    is HuiRootStatus.Locked -> "作弊哨兵已触发 · ${EnvironmentPurge.remainingLockText()}"
    is HuiRootStatus.Working -> status.detail
}

// 简单的内存缓存
private var lastBootState: BootloaderDetector.BootState? = null
private var lastChunqiuResult: ChunqiuDetector.DetectionResult? = null
private var lastCheckTime = 0L
private const val CHECK_CACHE_TTL = 30_000L

suspend fun getBootAndChunqiu(context: android.content.Context): Pair<BootloaderDetector.BootState, ChunqiuDetector.DetectionResult> {
    val now = System.currentTimeMillis()
    val boot = lastBootState
    val chunqiu = lastChunqiuResult
    if (boot != null && chunqiu != null && now - lastCheckTime < CHECK_CACHE_TTL) {
        return Pair(boot, chunqiu)
    }
    val newBoot = BootloaderDetector.detectBootState()
    val newChunqiu = ChunqiuDetector.comprehensiveCheck(context)
    lastBootState = newBoot
    lastChunqiuResult = newChunqiu
    lastCheckTime = now
    return Pair(newBoot, newChunqiu)
}
