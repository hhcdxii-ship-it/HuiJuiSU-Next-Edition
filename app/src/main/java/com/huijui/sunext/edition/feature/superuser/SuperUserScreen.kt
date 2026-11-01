package com.huijui.sunext.edition.feature.superuser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.RootApp
import com.huijui.sunext.edition.core.root.RootAppEntry
import com.huijui.sunext.edition.core.root.SuperUserRepository
import com.huijui.sunext.edition.core.ui.design.HuiAlpha
import com.huijui.sunext.edition.core.ui.design.HuiEmpty
import com.huijui.sunext.edition.core.ui.design.HuiError
import com.huijui.sunext.edition.core.ui.design.HuiLoading
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings

/**
 * 超级用户（Root 授权）页 —— 从"演示壳子"重构成真页面。
 *
 * 重构前：
 *   - 列表是 `mockApps = listOf("com.termux", "com.android.shell", "com.example.hacker")`，
 *     三条硬编码，跟设备上装了什么无关，也不显示图标；
 *   - 开关调用的 `SuperUserManager.saveAllowlist()` 因为 `init()` 从未被调用，
 *     `appContext` 为 null，**整个写入是空操作**，退出重进全部归零；
 *   - 主页「授权 N」读的是 `AppSettings.grantedApps`，跟这一页存的又不是同一份数据。
 *
 * 现在：PackageManager 真实枚举 + 真图标（LruCache）+ 搜索 + 已授权筛选，
 * 授权集合与主页共用 `AppSettings.grantedApps` 一份真相。
 */
@Composable
fun SuperUserScreen() {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color
    val grantedSet = AppSettings.grantedApps

    var query by rememberSaveable { mutableStateOf("") }
    var onlyGranted by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<RootAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        runCatching { SuperUserRepository.loadInstalledApps(context) }
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取应用列表失败" }
        loading = false
    }

    // 过滤 + 排序全部在本地算，不进 IO；toggle 时不需要重新扫包
    val visible = remember(entries, grantedSet, query, onlyGranted) {
        val q = query.trim()
        entries.asSequence()
            .filter { !onlyGranted || grantedSet.contains(it.packageName) }
            .filter { q.isEmpty() || it.label.contains(q, true) || it.packageName.contains(q, true) }
            .map { it.toRootApp(grantedSet.contains(it.packageName)) }
            .sortedWith(
                compareBy<RootApp> { it.isSystem }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 头部 ────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = HuiSpace.screen)) {
            Spacer(Modifier.height(HuiSpace.contentTop))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText("超级用户", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(2.dp))
                    ZakoText(
                        "SUPERUSER · 已授权 ${grantedSet.size}",
                        color = Color.White.huiTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(HuiSpace.l))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    ZakoText("搜索应用名或包名", color = Color.White.copy(alpha = 0.32f), fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    cursorColor = accent
                )
            )

            Spacer(Modifier.height(HuiSpace.m))

            Row(horizontalArrangement = Arrangement.spacedBy(HuiSpace.s)) {
                FilterChip(
                    text = "全部应用",
                    selected = !onlyGranted,
                    accent = accent,
                    onClick = { onlyGranted = false }
                )
                FilterChip(
                    text = "已授权 ${grantedSet.size}",
                    selected = onlyGranted,
                    accent = accent,
                    onClick = { onlyGranted = true }
                )
            }

            Spacer(Modifier.height(HuiSpace.m))
        }

        // ── 列表 ────────────────────────────────────────────────────
        val err = error
        when {
            loading -> HuiLoading(modifier = Modifier.padding(top = 48.dp), text = "正在读取已安装应用")
            err != null -> HuiError(
                message = err,
                modifier = Modifier.padding(horizontal = HuiSpace.screen),
                onRetry = { reloadKey++ }
            )
            visible.isEmpty() -> HuiEmpty(
                icon = Icons.Default.SearchOff,
                title = if (onlyGranted) "还没有授权任何应用" else "没有匹配的应用",
                subtitle = when {
                    onlyGranted -> "在上面搜索框里找到应用，把开关打开即可"
                    query.isNotEmpty() -> "换个关键词试试"
                    else -> "这台设备上没有读取到可授权的应用"
                },
                modifier = Modifier.padding(horizontal = HuiSpace.screen)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = HuiSpace.screen),
                contentPadding = PaddingValues(bottom = HuiSpace.contentBottom),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = visible, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        accent = accent,
                        onToggle = { granted ->
                            SuperUserRepository.setGranted(app.packageName, granted)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        ZakoText(
            text,
            color = if (selected) accent else Color.White.huiSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun AppRow(app: RootApp, accent: Color, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, size = 38.dp)

        Spacer(Modifier.width(HuiSpace.m))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZakoText(
                    app.label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystem) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        ZakoText("系统", color = Color.White.huiTertiary, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(1.dp))
            ZakoText(
                app.packageName,
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(HuiSpace.s))

        Switch(
            checked = app.granted,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }

    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName, sizePx) {
        value = SuperUserRepository.loadIcon(context, packageName, sizePx)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = HuiAlpha.HAIRLINE)),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        }
    }
}
