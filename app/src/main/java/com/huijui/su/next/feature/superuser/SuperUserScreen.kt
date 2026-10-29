package com.huijui.su.next.feature.superuser

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huijui.su.next.core.root.HuijuiDeviceBridge
import com.huijui.su.next.core.root.RootManager
import com.huijui.su.next.core.root.RootManagerDetector
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val appInfo: ApplicationInfo
)

@Composable
fun SuperUserScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var grantedSet by remember { mutableStateOf(AppSettings.grantedApps) }
    var currentManager by remember { mutableStateOf<RootManager?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val manager = RootManagerDetector.detect(forceRefresh = true)
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app -> pm.getLaunchIntentForPackage(app.packageName) != null }
                .map { app ->
                    AppInfoItem(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        appInfo = app
                    )
                }
                .sortedWith(
                    compareByDescending<AppInfoItem> { it.packageName in AppSettings.grantedApps }
                        .thenBy { it.appName.lowercase() }
                )

            withContext(Dispatchers.Main) {
                currentManager = manager
                allApps = installed
                isLoading = false
            }
        }
    }

    val filteredApps by remember(allApps, searchQuery, grantedSet) {
        derivedStateOf {
            if (searchQuery.isBlank()) allApps
            else allApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val grantedCount = grantedSet.size
    val totalCount = allApps.size

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("扫描已安装应用…", color = Color.White.copy(0.6f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                null,
                                tint = AppSettings.accentColor.color,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("超级用户", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "已授权 $grantedCount / $totalCount 个应用",
                            color = Color.White.copy(0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                item {
                    ManagerStatusCard(manager = currentManager)
                }

                item {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" }
                    )
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searchQuery.isBlank()) "未检测到可授权的应用"
                                else "未找到匹配「$searchQuery」的应用",
                                color = Color.White.copy(0.4f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { app ->
                    val isGranted = app.packageName in grantedSet
                    AppRow(
                        app = app,
                        isGranted = isGranted,
                        manager = currentManager,
                        onToggle = { wantGrant ->
                            val oldSet = grantedSet
                            grantedSet = if (wantGrant) {
                                grantedSet + app.packageName
                            } else {
                                grantedSet - app.packageName
                            }
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    if (wantGrant) {
                                        HuijuiDeviceBridge.grantRootToApp(context, app.packageName)
                                    } else {
                                        HuijuiDeviceBridge.revokeRootFromApp(context, app.packageName)
                                    }
                                }
                                if (success) {
                                    AppSettings.grantedApps = if (wantGrant) {
                                        AppSettings.grantedApps + app.packageName
                                    } else {
                                        AppSettings.grantedApps - app.packageName
                                    }
                                    AppSettings.save()
                                    val managerName = currentManager?.shortName ?: "未知"
                                    val msg = if (wantGrant) {
                                        "已写入 $managerName 授权：${app.appName}"
                                    } else {
                                        "已从 $managerName 撤销：${app.appName}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    grantedSet = oldSet
                                    val managerName = currentManager?.shortName ?: "未知"
                                    Toast.makeText(
                                        context,
                                        "操作失败：$managerName 授权通道不可用",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }

                item {
                    ManagerFooterHint(manager = currentManager)
                }
            }
        }
    }
}

// ==========================================
// 🚀 当前管理器状态卡片
// ==========================================
@Composable
private fun ManagerStatusCard(manager: RootManager?) {
    val managerName = manager?.displayName ?: "检测中…"
    val shortName = manager?.shortName ?: "—"
    val isUltra = manager == RootManager.HUIJUI_ULTRA
    val isHuiNext = manager == RootManager.HUIJUI
    val isNoRoot = manager == RootManager.NONE
    val isUnknown = manager == RootManager.UNKNOWN

    val (statusText, statusColor, modeLabel) = when {
        isNoRoot -> Triple("未检测到 Root", Color(0xFFEF4444), "无 Root")
        isUltra -> Triple("HuiJuiSU Ultra 内核态接管中", Color(0xFF81C784), "内核态")
        isHuiNext -> Triple("HuiJuiSU Next 内核态接管中", Color(0xFF81C784), "内核态")
        isUnknown -> Triple("未知管理器接管", Color(0xFFFFC107), "兼容模式")
        else -> Triple("$managerName 接管中", Color(0xFF64B5F6), "兼容模式")
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, tint = statusColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "当前 Root 管理器",
                        color = Color.White.copy(0.5f),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = if (statusText.length > 20) 13.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        modeLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isUltra) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "HuiJuiSU Ultra 内核已接管，Next 与 Ultra 共享同一内核驱动 /dev/huijui",
                    color = Color.White.copy(0.45f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            } else if (isHuiNext) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "HuiJuiSU Next 内核已接管，授权写入 /dev/huijui",
                    color = Color.White.copy(0.45f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            } else if (!isNoRoot) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "HUI 正在以兼容模式运行，授权会写入 $shortName 的策略表，可在 $managerName App 中查看",
                    color = Color.White.copy(0.45f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ==========================================
// 🚀 底部说明
// ==========================================
@Composable
private fun ManagerFooterHint(manager: RootManager?) {
    val hint = when (manager) {
        RootManager.HUIJUI_ULTRA ->
            "✅ HuiJuiSU Ultra 内核态接管中，与 Next 共享 /dev/huijui，授权互通"
        RootManager.HUIJUI ->
            "✅ HuiJuiSU Next 内核态接管中，撤销授权需等待 HUI.ko 支持 unlink 后生效"
        RootManager.NONE -> "⚠️ 未检测到 Root，授权功能不可用"
        RootManager.UNKNOWN -> "⚠️ 未知管理器，授权尝试写入 KSU/Magisk/APatch 通用路径"
        RootManager.KERNELSU, RootManager.KERNELSU_NEXT,
        RootManager.SUKISU_ULTRA, RootManager.BAKASU,
        RootManager.YUKISU, RootManager.KOWSU,
        RootManager.RKSU, RootManager.MKSU, RootManager.WILDKSU ->
            "✅ KernelSU 系接管中，授权写入 /data/adb/ksu/package_name"
        RootManager.APATCH, RootManager.ASTER, RootManager.FOLKPATCH ->
            "✅ APatch 系接管中，授权写入 /data/adb/ap/package_name"
        RootManager.MAGISK, RootManager.MAGISK_WEAVEMASK,
        RootManager.MAGISK_KITSUNE, RootManager.MAGISK_ALPHA, RootManager.MAGISK_DELTA ->
            "✅ Magisk 系接管中，授权写入 magisk.db policies 表"
        RootManager.SHIZUKU ->
            "⚠️ Shizuku 不是完整 Root 管理器，无法直接授权"
        null -> "正在检测 Root 管理器…"
        else -> "✅ ${manager.displayName} 接管中，授权通道已就绪"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 4.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Info,
                null,
                tint = Color.White.copy(0.3f),
                modifier = Modifier.size(14.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                hint,
                color = Color.White.copy(0.35f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ==========================================
// 🧩 搜索框
// ==========================================
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val accent = AppSettings.accentColor.color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.08f))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索应用名或包名", color = Color.White.copy(0.35f), fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Clear,
                    null,
                    tint = Color.White.copy(0.5f),
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onClear() }
                )
            }
        }
    }
}

// ==========================================
// 🧩 应用行
// ==========================================
@Composable
private fun AppRow(
    app: AppInfoItem,
    isGranted: Boolean,
    manager: RootManager?,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.98f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "appRowScale"
    )

    val borderAlpha by animateFloatAsState(
        if (isGranted) 0.4f else 0.08f,
        tween(250),
        label = "appRowBorder"
    )
    val borderColor = if (isGranted) accent.copy(alpha = borderAlpha) else Color.White.copy(alpha = borderAlpha)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        onClick = { onToggle(!isGranted) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.08f))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(app.appInfo)
                        .crossfade(true)
                        .build(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    color = Color.White.copy(0.4f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = isGranted,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Color.White.copy(0.5f),
                    uncheckedTrackColor = Color.White.copy(0.1f),
                    uncheckedBorderColor = Color.White.copy(0.2f)
                )
            )
        }
    }
}
