package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.language.ZakoText

import android.content.Context
import android.content.pm.ApplicationInfo
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.huijui.sunext.edition.core.root.AppManagerProvider
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.layout.HuijuiLayout
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppFilterTab(val label: String) {
    ALL("全部"),
    SYSTEM("系统"),
    USER("用户"),
    DISABLED("已冻结")
}

@Composable
fun AppManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scanResult by remember { mutableStateOf<AppManagerProvider.ScanResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(AppFilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<Triple<AppManagerProvider.ManagedApp, String, String>?>(null) }

    suspend fun reload() {
        isLoading = true
        val r = withContext(Dispatchers.IO) { AppManagerProvider.scanApps(context, includeSystem = true) }
        scanResult = r
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    val filteredApps = remember(scanResult, selectedTab, searchQuery) {
        scanResult?.apps?.filter { app ->
            val tabMatch = when (selectedTab) {
                AppFilterTab.ALL -> true
                AppFilterTab.SYSTEM -> app.isSystem
                AppFilterTab.USER -> !app.isSystem
                AppFilterTab.DISABLED -> app.isDisabled || app.isUninstalledForUser
            }
            val searchMatch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            tabMatch && searchMatch
        } ?: emptyList()
    }

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
                    ZakoText("App 管理", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    ZakoText(
                        "冻结毒瘤 · 卸载系统应用（可恢复）",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
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

            if (isLoading || scanResult == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppSettings.accentColor.color)
                        Spacer(modifier = Modifier.height(16.dp))
                        ZakoText("扫描已安装应用…", color = Color.White.copy(0.6f), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(HuijuiLayout.CardSpacingDense),
                    contentPadding = HuijuiLayout.ScreenContentPadding
                ) {
                    // 统计卡片
                    item {
                        StatsCard(scanResult!!)
                    }

                    // Tab 栏
                    item {
                        TabRow(
                            selected = selectedTab,
                            onSelect = { selectedTab = it }
                        )
                    }

                    // 搜索
                    item {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClear = { searchQuery = "" }
                        )
                    }

                    // 空状态
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ZakoText(
                                    if (searchQuery.isNotBlank()) "未找到匹配「$searchQuery」的应用"
                                    else "该分类下没有应用",
                                    color = Color.White.copy(0.4f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // 应用列表
                    items(items = filteredApps, key = { it.packageName }) { app ->
                        AppManageCard(
                            app = app,
                            isBusy = busyPackage == app.packageName,
                            onAction = { actionType, actionLabel ->
                                pendingAction = Triple(app, actionType, actionLabel)
                            }
                        )
                    }
                }
            }
        }

        // 二次确认 Dialog
        pendingAction?.let { (app, actionType, actionLabel) ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingAction = null },
                title = {
                    ZakoText(
                        "⚠️ $actionLabel",
                        color = if (actionType in setOf("freeze", "uninstall")) Color(0xFFEF4444) else Color.White
                    )
                },
                text = {
                    ZakoText(
                        buildString {
                            appendLine("应用：${app.appName}")
                            appendLine("包名：${app.packageName}")
                            appendLine()
                            when (actionType) {
                                "freeze" -> append("冻结后应用将被禁用，无法运行，可随时解冻恢复。")
                                "unfreeze" -> append("解冻后应用恢复原状，可正常运行。")
                                "uninstall" -> append("将从当前用户卸载（系统分区不动），可通过「恢复」重新安装。")
                                "restore" -> append("从系统分区重新安装，应用将恢复到系统应用列表。")
                            }
                        },
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingAction = null
                        scope.launch {
                            busyPackage = app.packageName
                            val result = withContext(Dispatchers.IO) {
                                when (actionType) {
                                    "freeze" -> AppManagerProvider.freezeApp(app.packageName)
                                    "unfreeze" -> AppManagerProvider.unfreezeApp(app.packageName)
                                    "uninstall" -> AppManagerProvider.uninstallSystemApp(app.packageName)
                                    "restore" -> AppManagerProvider.restoreSystemApp(app.packageName)
                                    else -> Result.failure(Exception("未知操作"))
                                }
                            }
                            busyPackage = null
                            Toast.makeText(
                                context,
                                result.getOrElse { it.message ?: "操作失败" },
                                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                            ).show()
                            if (result.isSuccess) reload()
                        }
                    }) {
                        ZakoText(
                            "确认$actionLabel",
                            color = if (actionType in setOf("freeze", "uninstall")) Color(0xFFEF4444) else Color(0xFF81C784),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingAction = null }) {
                        ZakoText("取消", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }
}

// ==========================================
// 统计卡片
// ==========================================
@Composable
private fun StatsCard(result: AppManagerProvider.ScanResult) {
    val accent = AppSettings.accentColor.color
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatItem("总计", result.totalCount.toString(), accent, Modifier.weight(1f))
            VerticalDivider()
            StatItem("系统", result.systemCount.toString(), Color(0xFF64B5F6), Modifier.weight(1f))
            VerticalDivider()
            StatItem("用户", result.userCount.toString(), Color(0xFF81C784), Modifier.weight(1f))
            VerticalDivider()
            StatItem("已冻结", result.disabledCount.toString(), Color(0xFFFFC107), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ZakoText(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        ZakoText(label, color = Color.White.copy(0.5f), fontSize = 10.sp)
    }
}

@Composable
private fun VerticalDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.08f)))
}

// ==========================================
// Tab 栏
// ==========================================
@Composable
private fun TabRow(selected: AppFilterTab, onSelect: (AppFilterTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppFilterTab.values().forEach { tab ->
            val isSelected = tab == selected
            val bgColor by animateColorAsState(
                if (isSelected) AppSettings.accentColor.color.copy(alpha = 0.25f)
                else Color.White.copy(0.08f),
                tween(200), label = "tabBg_${tab.name}"
            )
            val textColor by animateColorAsState(
                if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.6f),
                tween(200), label = "tabText_${tab.name}"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                ZakoText(
                    tab.label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ==========================================
// 搜索框
// ==========================================
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
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
                    ZakoText("搜索应用名或包名", color = Color.White.copy(0.35f), fontSize = 14.sp)
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
                    Icons.Default.Clear, null, tint = Color.White.copy(0.5f),
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onClear() }
                )
            }
        }
    }
}

// ==========================================
// App 管理卡片
// ==========================================
@Composable
private fun AppManageCard(
    app: AppManagerProvider.ManagedApp,
    isBusy: Boolean,
    onAction: (String, String) -> Unit
) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color
    val isCritical = AppManagerProvider.isCritical(app.packageName)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.98f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "appCardScale"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 应用图标
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusMedium))
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(HuijuiLayout.IconRadiusMedium)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(app.appInfo).crossfade(true).build(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(HuijuiLayout.IconRadiusSmall))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                // 名称 + 包名
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZakoText(
                            app.appName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (app.isSystem) {
                            Badge("系统", Color(0xFF64B5F6))
                        }
                        if (app.isDisabled) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge("已冻结", Color(0xFFFFC107))
                        }
                        if (app.isUninstalledForUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge("已卸载", Color(0xFFEF4444))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    ZakoText(
                        app.packageName,
                        color = Color.White.copy(0.4f),
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCritical) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        ZakoText(
                            "🔒 核心系统组件，禁止操作",
                            color = Color.White.copy(0.4f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    if (app.isDisabled) {
                        ActionChip("解冻", Color(0xFF81C784), "unfreeze", "解冻", onAction, Modifier.weight(1f))
                    } else {
                        ActionChip("冻结", Color(0xFFFFC107), "freeze", "冻结", onAction, Modifier.weight(1f))
                    }
                    if (app.isUninstalledForUser) {
                        ActionChip("恢复", Color(0xFF64B5F6), "restore", "恢复", onAction, Modifier.weight(1f))
                    } else if (app.isSystem) {
                        ActionChip("卸载", Color(0xFFEF4444), "uninstall", "卸载", onAction, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        ZakoText(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionChip(
    label: String,
    color: Color,
    actionType: String,
    actionLabel: String,
    onAction: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.95f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "chipScale_$label"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onAction(actionType, actionLabel) },
        contentAlignment = Alignment.Center
    ) {
        ZakoText(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
