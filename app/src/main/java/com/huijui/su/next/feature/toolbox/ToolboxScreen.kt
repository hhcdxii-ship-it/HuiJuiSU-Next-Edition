package com.huijui.su.next.feature.toolbox

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.DeveloperMode
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.DeviceInfoProvider
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.root.RootManager
import com.huijui.su.next.core.root.RootManagerDetector
import com.huijui.su.next.core.ui.adaptive.AdaptiveLayoutInfo
import com.huijui.su.next.core.ui.adaptive.rememberAdaptiveLayoutInfo
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.rememberPausableInfiniteTransition
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceInfo(val brand: String = "未知", val model: String = "未知", val androidVer: String = "未知", val kernelVer: String = "未知", val fingerprint: String = "未知", val selinux: String = "Unknown")
enum class ManagerState(val title: String, val color: Color) { WORKING_HUIJUI("工作中", Color(0xFF81C784)), PENDING_VERIFY("等待验证", Color(0xFFFFC107)), OTHER_MANAGER("其他管理器", Color(0xFF64B5F6)), NOT_WORKING("未获取权限", Color(0xFFE57373)) }
data class LauncherAction(val icon: ImageVector, val label: String, val key: String)

@Composable
fun ToolboxScreen(
    onNavigateToFlash: () -> Unit = {},
    onNavigateToModule: () -> Unit = {},
    onNavigateToHotInstall: () -> Unit = {}
) {
    val context = LocalContext.current
    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var managerState by remember { mutableStateOf(ManagerState.NOT_WORKING) }
    var detectedManager by remember { mutableStateOf<RootManager?>(null) }
    var managerTitle by remember { mutableStateOf("未获取权限") }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isLauncherMode by remember { mutableStateOf(AppSettings.defaultLauncherMode) }
    var animationTrigger by remember { mutableStateOf(false) }
    var showBootSplash by remember { mutableStateOf(true) }
    val adaptiveInfo = rememberAdaptiveLayoutInfo()
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val detected = RootManagerDetector.detect(forceRefresh = refreshTrigger > 0)
            val isRooted = HuijuiShell.isRooted()
            val info = DeviceInfoProvider.get(forceRefresh = refreshTrigger > 0)
            withContext(Dispatchers.Main) {
                detectedManager = detected
                deviceInfo = DeviceInfo(info.brand, info.model, info.androidVer, info.kernelVer, info.fingerprint, info.selinux)
                managerState = if (!isRooted || detected == RootManager.NONE) ManagerState.NOT_WORKING else ManagerState.WORKING_HUIJUI
                managerTitle = when (managerState) {
                    ManagerState.WORKING_HUIJUI -> when {
                        detected == RootManager.HUIJUI_ULTRA -> "工作中（HUI-Ultra）"
                        detected == RootManager.HUIJUI -> "工作中（HUI）"
                        else -> "工作中（${detected?.shortName}）"
                    }
                    else -> managerState.title
                }
                isLoading = false; delay(50); animationTrigger = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
        } else {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(adaptiveInfo.hingePadding)) {
                if (!showBootSplash) {
                    IconButton(
                        onClick = { animationTrigger = false; isLauncherMode = !isLauncherMode; if (isLauncherMode) showBootSplash = true; scope.launch { delay(60); animationTrigger = true } },
                        modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).clip(CircleShape).background(Color.White.copy(0.05f))
                    ) { Icon(if (isLauncherMode) Icons.Default.ViewList else Icons.Default.GridView, "切换", tint = Color.White.copy(0.7f)) }
                }

                AnimatedContent(targetState = isLauncherMode, transitionSpec = { fadeIn(tween(300)) + slideInVertically { it / 8 } togetherWith fadeOut(tween(250)) }) { launcherMode ->
                    if (launcherMode) {
                        if (showBootSplash) LauncherBootSplash(detectedManager) { showBootSplash = false }
                        else LauncherLayout(deviceInfo, managerState, managerTitle, animationTrigger, refreshTrigger, adaptiveInfo, onRefresh = { refreshTrigger++ }, onNavigateToFlash, onNavigateToModule, onNavigateToHotInstall)
                    } else {
                        ClassicLayout(deviceInfo, managerState, managerTitle, animationTrigger, refreshTrigger, adaptiveInfo, onRefresh = { refreshTrigger++ }, onNavigateToFlash, onNavigateToModule, onNavigateToHotInstall)
                    }
                }
            }
        }
    }
}

// ==========================================
// 🚀 启动器模式（赛博 HUD 驾驶舱）
// ==========================================
@Composable
fun LauncherLayout(
    deviceInfo: DeviceInfo, managerState: ManagerState, managerTitle: String, trigger: Boolean, refreshTrigger: Int, adaptiveInfo: AdaptiveLayoutInfo, onRefresh: () -> Unit,
    onNavigateToFlash: () -> Unit, onNavigateToModule: () -> Unit, onNavigateToHotInstall: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) { if (trigger) { delay(100); visible = true } }

    // 实时时钟
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = sdf.format(Date())
            delay(1000)
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(800))) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            
            // 1. 顶部状态 HUD
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("HUI SYSTEM", color = AppSettings.accentColor.color.copy(0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text("ONLINE", color = managerState.color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(currentTime, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
                    Text(deviceInfo.model, color = Color.White.copy(0.4f), fontSize = 10.sp)
                }
            }

            // 2. 中间全息能量球
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                HolographicCore(managerState, managerTitle, AppSettings.grantedApps.size) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onRefresh() }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // 3. 三大核心操作（横向胶囊）
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoreActionCapsule("刷机", Icons.Default.Memory, Color(0xFF64B5F6), onNavigateToFlash, Modifier.weight(1f))
                    CoreActionCapsule("模块", Icons.Default.Folder, Color(0xFF81C784), onNavigateToModule, Modifier.weight(1f))
                    CoreActionCapsule("接管", Icons.Default.FlashOn, Color(0xFFEF4444), onNavigateToHotInstall, Modifier.weight(1f))
                }
            }

            // 4. 底部极简 Dock
            val dockActions = listOf(
                LauncherAction(Icons.Default.Terminal, "终端", "term"),
                LauncherAction(Icons.Default.Bolt, "指令", "cmd"),
                LauncherAction(Icons.Default.Apps, "应用", "app"),
                LauncherAction(Icons.Default.Speed, "性能", "cpu")
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Color.White.copy(0.06f))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                dockActions.forEach { action ->
                    DockIcon(action) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); Toast.makeText(context, action.label, Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

// ==========================================
// 🌐 全息能量球组件
// ==========================================
@Composable
fun HolographicCore(state: ManagerState, title: String, appCount: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val clickScale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.3f, stiffness = 500f), label = "clickScale")
    
    val infinite = rememberPausableInfiniteTransition(label = "hud")
    val rotate1 by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "rot1")
    val rotate2 by infinite.animateFloat(360f, 0f, infiniteRepeatable(tween(15000, easing = LinearEasing)), label = "rot2")
    val breath by infinite.animateFloat(0.8f, 1.1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        // 背景光晕
        Box(modifier = Modifier.size(200.dp).graphicsLayer { scaleX = breath; scaleY = breath }.background(Brush.radialGradient(listOf(state.color.copy(0.2f), Color.Transparent)), CircleShape))
        
        // 外围科技环 1（虚线）
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotate1 }) {
            drawArc(
                color = state.color.copy(0.4f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(10.dp.toPx(), 10.dp.toPx()), size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
            )
        }
        
        // 外围科技环 2（实线缺口）
        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp).graphicsLayer { rotationZ = rotate2 }) {
            drawArc(color = Color.White.copy(0.2f), startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(1.dp.toPx()))
            drawArc(color = Color.White.copy(0.2f), startAngle = 135f, sweepAngle = 270f, useCenter = false, style = Stroke(1.dp.toPx()))
        }

        // 中心实体球
        Box(
            modifier = Modifier.size(140.dp).scale(clickScale)
                .shadow(if (isPressed) 5.dp else 30.dp, CircleShape, ambientColor = state.color, spotColor = state.color)
                .clip(CircleShape).background(Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.Black.copy(0.7f))))
                .border(1.5.dp, Brush.linearGradient(listOf(state.color, Color.Transparent, state.color)), CircleShape)
                .clickable(interactionSource = interactionSource, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Security, null, tint = state.color, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = title, color = Color.White, fontSize = if (title.length > 6) 13.sp else 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("$appCount AUTH", color = state.color.copy(0.8f), fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
    }
}

// ==========================================
// 💊 核心操作胶囊
// ==========================================
@Composable
fun CoreActionCapsule(label: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "capsule")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(64.dp).scale(scale)
            .clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// 📱 底部 Dock 图标
// ==========================================
@Composable
fun DockIcon(action: LauncherAction, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.8f else 1f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "dock")
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() }.padding(8.dp)) {
        Icon(action.icon, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(24.dp).scale(scale))
        Spacer(modifier = Modifier.height(4.dp))
        Text(action.label, color = Color.White.copy(0.6f), fontSize = 10.sp)
    }
}

// ==========================================
// 💼 经典模式（仪表盘）
// ==========================================
@Composable
fun ClassicLayout(
    deviceInfo: DeviceInfo, managerState: ManagerState, managerTitle: String, trigger: Boolean, refreshTrigger: Int, adaptiveInfo: AdaptiveLayoutInfo, onRefresh: () -> Unit,
    onNavigateToFlash: () -> Unit, onNavigateToModule: () -> Unit, onNavigateToHotInstall: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            AnimatedCard(0, trigger) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HuiJuiSU", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                            Text("OMNI-ROOT ENGINE", color = AppSettings.accentColor.color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(AppSettings.accentColor.color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Unified", color = AppSettings.accentColor.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            AnimatedCard(1, trigger) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(0.08f)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(managerState.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, null, tint = managerState.color, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(managerTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("已接管 ${AppSettings.grantedApps.size} 个应用", color = Color.White.copy(0.4f), fontSize = 12.sp)
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            AnimatedCard(2, trigger) {
                Row(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(AppSettings.accentColor.color.copy(alpha = 0.15f)).clickable { onNavigateToFlash() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Memory, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("刷入镜像", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { AnimatedCard(3, trigger) { ClassicQuickBtn("装模块", Icons.Default.Folder, onNavigateToModule) } }
        item { AnimatedCard(4, trigger) { ClassicQuickBtn("极速热接管", Icons.Default.FlashOn, onNavigateToHotInstall) } }
        item { AnimatedCard(5, trigger) { InfoGridCard(Icons.TwoTone.PhoneAndroid, "设备", "${deviceInfo.brand} ${deviceInfo.model}") } }
        item { AnimatedCard(6, trigger) { InfoGridCard(Icons.TwoTone.DeveloperMode, "系统", "Android ${deviceInfo.androidVer}") } }
        item { AnimatedCard(7, trigger) { InfoGridCard(Icons.TwoTone.Build, "内核", deviceInfo.kernelVer) } }
        item { AnimatedCard(8, trigger) { InfoGridCard(Icons.Default.Fingerprint, "指纹", deviceInfo.fingerprint) } }
    }
}

@Composable
fun ClassicQuickBtn(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.06f)).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun InfoGridCard(icon: ImageVector, title: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.06f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, color = Color.White.copy(0.4f), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AnimatedCard(index: Int, trigger: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) { if (trigger) { delay(index * 50L); visible = true } }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "alpha")
    val offsetY by animateFloatAsState(if (visible) 0f else 30f, spring(dampingRatio = 0.75f, stiffness = 200f), label = "offsetY")
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha; translationY = offsetY }) { content() }
}

@Composable
fun LauncherBootSplash(detectedManager: RootManager?, onAnimationFinished: () -> Unit) {
    var textStep by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { delay(800); haptic.performHapticFeedback(HapticFeedbackType.LongPress); textStep = 1; delay(600); textStep = 2; delay(500); onAnimationFinished() }
    val infiniteTransition = rememberPausableInfiniteTransition(label = "splash")
    val coreScale by infiniteTransition.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "coreScale")
    val coreAlpha by infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "coreAlpha")
    
    AnimatedVisibility(visible = textStep < 2, exit = fadeOut(tween(400))) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(120.dp).scale(coreScale).border(2.dp, AppSettings.accentColor.color.copy(alpha = coreAlpha), CircleShape))
                    Box(modifier = Modifier.size(90.dp).border(1.dp, Color.White.copy(0.2f), CircleShape))
                    Icon(Icons.Default.Security, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.height(30.dp))
                if (textStep >= 1) {
                    Text(if (detectedManager == RootManager.HUIJUI_ULTRA) "HUI ULTRA INITIALIZING" else "HUI SYSTEM INITIALIZING", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("> Root Channel Connected", color = AppSettings.accentColor.color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
