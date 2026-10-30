package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.language.ZakoText

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.CpuInfoProvider
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.SystemMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CpuControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<CpuInfoProvider.CpuStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isChanging by remember { mutableStateOf(false) }
    var monitoringEnabled by remember { mutableStateOf(AppSettings.systemMonitorEnabled) }

    LaunchedEffect(Unit) {
        while (true) {
            val newStatus = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
            status = newStatus
            isLoading = false
            delay(3000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))

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
                    ZakoText("CPU 调度", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    ZakoText("实时频率 · 性能模式 · 调度器", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.1f))
                        .clickable {
                            scope.launch {
                                isLoading = true
                                status = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
                                isLoading = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, "刷新", tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading || status == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppSettings.accentColor.color)
                        Spacer(modifier = Modifier.height(16.dp))
                        ZakoText("读取 CPU 状态…", color = Color.White.copy(0.6f), fontSize = 13.sp)
                    }
                }
            } else {
                val s = status!!
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
                ) {
                    item {
                        CpuStatusCard(status = s)
                    }

                    // ---- 系统监控开关（新）----
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF64B5F6).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    ZakoText("系统监控通知", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    ZakoText(
                                        if (monitoringEnabled) "已开启：通知栏常驻 CPU/温度"
                                        else "已关闭：不显示常驻通知",
                                        color = if (monitoringEnabled) Color(0xFF64B5F6) else Color.White.copy(0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = monitoringEnabled,
                                    onCheckedChange = { want ->
                                        monitoringEnabled = want
                                        AppSettings.systemMonitorEnabled = want
                                        AppSettings.save()
                                        if (want) {
                                            // Android 13+ 需要 POST_NOTIFICATIONS 权限
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val hasPermission = context.checkSelfPermission(
                                                    android.Manifest.permission.POST_NOTIFICATIONS
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (!hasPermission) {
                                                    Toast.makeText(
                                                        context,
                                                        "请到系统设置中授予通知权限",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    // 仍然尝试启动，用户可能已经有权限
                                                }
                                            }
                                            SystemMonitorService.start(context)
                                            Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("监控通知已开启"), Toast.LENGTH_SHORT).show()
                                        } else {
                                            SystemMonitorService.stop(context)
                                            Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("监控通知已关闭"), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF64B5F6),
                                        uncheckedThumbColor = Color.White.copy(0.5f),
                                        uncheckedTrackColor = Color.White.copy(0.1f)
                                    )
                                )
                            }
                        }
                    }

                    // ---- 性能模式开关 ----
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Speed, null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    ZakoText("性能模式", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    ZakoText(
                                        if (s.performanceMode) "已开启：解锁满血性能" else "已关闭：正常调度",
                                        color = if (s.performanceMode) Color(0xFFEF4444) else Color.White.copy(0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = s.performanceMode,
                                    enabled = !isChanging,
                                    onCheckedChange = { want ->
                                        scope.launch {
                                            isChanging = true
                                            val ok = withContext(Dispatchers.IO) {
                                                CpuInfoProvider.setPerformanceMode(want)
                                            }
                                            status = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
                                            isChanging = false
                                            Toast.makeText(
                                                context,
                                                if (ok) "性能模式已${if (want) "开启" else "关闭"}"
                                                else "切换失败，请确认 HUI 内核已加载",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFEF4444),
                                        uncheckedThumbColor = Color.White.copy(0.5f),
                                        uncheckedTrackColor = Color.White.copy(0.1f)
                                    )
                                )
                            }
                        }
                    }

                    // ---- 三档快捷切换 ----
                    item {
                        ZakoText(
                            "快捷档位",
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickModeChip(
                                label = "省电",
                                emoji = "🌱",
                                active = s.currentGovernor == "powersave" && !s.performanceMode,
                                accent = Color(0xFF81C784),
                                enabled = !isChanging,
                                onClick = {
                                    scope.launch {
                                        isChanging = true
                                        withContext(Dispatchers.IO) {
                                            CpuInfoProvider.setPerformanceMode(false)
                                            CpuInfoProvider.setGovernor("powersave")
                                        }
                                        status = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
                                        isChanging = false
                                        Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("已切换到省电模式"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            QuickModeChip(
                                label = "均衡",
                                emoji = "⚖️",
                                active = s.currentGovernor == "schedutil" && !s.performanceMode,
                                accent = Color(0xFF64B5F6),
                                enabled = !isChanging,
                                onClick = {
                                    scope.launch {
                                        isChanging = true
                                        withContext(Dispatchers.IO) {
                                            CpuInfoProvider.setPerformanceMode(false)
                                            CpuInfoProvider.setGovernor("schedutil")
                                        }
                                        status = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
                                        isChanging = false
                                        Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("已切换到均衡模式"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            QuickModeChip(
                                label = "性能",
                                emoji = "🚀",
                                active = s.currentGovernor == "performance" || s.performanceMode,
                                accent = Color(0xFFEF4444),
                                enabled = !isChanging,
                                onClick = {
                                    scope.launch {
                                        isChanging = true
                                        withContext(Dispatchers.IO) {
                                            CpuInfoProvider.setPerformanceMode(true)
                                            CpuInfoProvider.setGovernor("performance")
                                        }
                                        status = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
                                        isChanging = false
                                        Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("已切换到性能模式"), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ---- 调度器列表 ----
                    item {
                        ZakoText(
                            "调度器（当前：${s.currentGovernor}）",
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                        )
                    }

                    if (s.availableGovernors.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                ZakoText(
                                    "未检测到可用调度器列表，可能是 HUI 内核未加载",
                                    color = Color.White.copy(0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(items = s.availableGovernors, key = { it }) { gov ->
                            GovernorRow(
                                governor = gov,
                                selected = gov == s.currentGovernor,
                                enabled = !isChanging,
                                onClick = {
                                    scope.launch {
                                        isChanging = true
                                        val ok = withContext(Dispatchers.IO) {
                                            CpuInfoProvider.setGovernor(gov)
                                        }
                                        status = withContext(Dispatchers.IO) { CpuInfoProvider.getStatus() }
                                        isChanging = false
                                        Toast.makeText(
                                            context,
                                            if (ok) "已切换到 $gov" else "切换失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuStatusCard(status: CpuInfoProvider.CpuStatus) {
    val accent = AppSettings.accentColor.color

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Thermostat, null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText(
                        if (status.temperature > 0) "${"%.1f".format(status.temperature)}°C" else "温度未知",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ZakoText("CPU 温度", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    ZakoText(
                        status.currentGovernor,
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    ZakoText("当前调度器", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (status.cores.isNotEmpty()) {
                val bigCoreMaxMhz = status.bigCoreMaxFreq / 1000f
                val littleCoreMaxMhz = status.littleCoreMaxFreq / 1000f
                val isHeterogeneous = bigCoreMaxMhz > littleCoreMaxMhz * 1.2f

                status.cores.forEach { core ->
                    val isBig = isHeterogeneous && core.maxFreq >= status.bigCoreMaxFreq * 0.9f
                    val maxMhz = core.maxFreq / 1000f
                    val curMhz = core.curFreq / 1000f
                    val ratio = if (maxMhz > 0) (curMhz / maxMhz).coerceIn(0f, 1f) else 0f
                    val animatedRatio by animateFloatAsState(
                        targetValue = ratio,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "core_${core.id}"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZakoText(
                            "CPU${core.id}",
                            color = Color.White.copy(0.6f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(52.dp)
                        )
                        ZakoText(
                            if (isBig) "大核" else "小核",
                            color = if (isBig) Color(0xFFEF4444) else Color(0xFF64B5F6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(36.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedRatio)
                                    .height(10.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            if (isBig) {
                                                listOf(Color(0xFFEF4444).copy(0.6f), Color(0xFFEF4444))
                                            } else {
                                                listOf(Color(0xFF64B5F6).copy(0.6f), Color(0xFF64B5F6))
                                            }
                                        )
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ZakoText(
                            "${curMhz.toInt()}M",
                            color = Color.White.copy(0.75f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(52.dp)
                        )
                    }
                }
            }

            if (!status.isKernelAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                ZakoText(
                    "⚠️ HUI 内核未接管，数据来自 sysfs，部分操作走 fallback",
                    color = Color(0xFFFFC107),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun QuickModeChip(
    label: String,
    emoji: String,
    active: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.94f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "quick_$label"
    )
    val bgColor = if (active) accent.copy(alpha = 0.3f) else Color.White.copy(0.08f)

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .then(
                if (active) {
                    Modifier.background(
                        Brush.linearGradient(listOf(accent.copy(0.15f), Color.Transparent))
                    )
                } else Modifier
            )
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZakoText(emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(6.dp))
        ZakoText(
            label,
            color = if (active) accent else Color.White.copy(0.75f),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun GovernorRow(
    governor: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val accent = AppSettings.accentColor.color
    val desc = when (governor) {
        "schedutil" -> "Android 首选，动态响应，均衡"
        "performance" -> "锁最高频，性能最强，耗电"
        "powersave" -> "锁最低频，最省电，卡"
        "ondemand" -> "按需调整，响应稍慢"
        "conservative" -> "渐进调整，省电优先"
        "interactive" -> "经典交互优先调度"
        "userspace" -> "由用户态手动控制"
        else -> "其他调度策略"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else 0.6f },
        onClick = if (enabled) onClick else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.White.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(
                    governor,
                    color = if (selected) accent else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                ZakoText(desc, color = Color.White.copy(0.5f), fontSize = 11.sp)
            }
        }
    }
}
