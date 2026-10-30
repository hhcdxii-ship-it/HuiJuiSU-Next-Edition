package com.huijui.sunext.edition.feature.superuser

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.huijui.sunext.edition.core.root.HuijuiDeviceBridge
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.root.KsuBridge
import com.huijui.sunext.edition.core.root.RootManager
import com.huijui.sunext.edition.core.root.RootManagerDetector
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.language.zakoString
import com.huijui.sunext.edition.core.utils.AppSettings
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
    var isSyncing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var grantedSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentManager by remember { mutableStateOf<RootManager?>(null) }

    suspend fun syncData() {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
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
                    .sortedBy { it.appName.lowercase() }

                withContext(Dispatchers.Main) {
                    allApps = installed
                    isLoading = false
                }

                // 暴力同步底层
                val manager = RootManagerDetector.detect(forceRefresh = true)
                val realGranted = mutableSetOf<String>()
                realGranted.addAll(AppSettings.grantedApps)

                // 🚀 优先走 KSU 官方 .allowlist（strings 抓包名，Kotlin 侧正则过滤）
                val ksuGranted = KsuBridge.listGranted()
                realGranted.addAll(ksuGranted)

                // 🛟 兜底：KSU 为空时尝试 Magisk sqlite
                if (ksuGranted.isEmpty()) {
                    val magiskResult = HuijuiShell.exec("su -c magisk --sqlite \"SELECT package_name FROM policies WHERE policy=2\"", useCache = false)
                    if (magiskResult.isSuccess) {
                        magiskResult.out.forEach {
                            val clean = it.trim().replace("package_name=", "")
                            if (clean.isNotBlank()) realGranted.add(clean)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    currentManager = manager
                    grantedSet = realGranted
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    LaunchedEffect(Unit) { syncData() }

    val filteredApps by remember(allApps, searchQuery, grantedSet) {
        derivedStateOf {
            val baseList = if (searchQuery.isBlank()) allApps
            else allApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }

            // 🚀 极客排序：已授权的置顶，未授权的沉底；同状态按应用名排
            baseList.sortedWith(
                compareByDescending<AppInfoItem> { grantedSet.contains(it.packageName) }
                    .thenBy { it.appName }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                    Spacer(modifier = Modifier.height(16.dp))
                    ZakoText("正在扫你的垃圾应用…", color = Color.White.copy(0.6f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        ZakoText("超级用户", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        // 🚀 强行手动同步按钮
                        IconButton(onClick = {
                            scope.launch {
                                isSyncing = true
                                syncData()
                                isSyncing = false
                                Toast.makeText(context, zakoString("同步完成"), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            if (isSyncing) CircularProgressIndicator(color = AppSettings.accentColor.color, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "同步底层", tint = Color.White.copy(0.8f))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ZakoText("${grantedSet.size} / ${allApps.size} 个应用已授权", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }

                item { ManagerStatusCard(manager = currentManager, grantedCount = grantedSet.size) }
                item { SearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, onClear = { searchQuery = "" }) }

                items(items = filteredApps, key = { it.packageName }) { app ->
                    val isGranted = app.packageName in grantedSet
                    AppRow(
                        app = app,
                        isGranted = isGranted,
                        onToggle = { wantGrant ->
                            val oldSet = grantedSet
                            grantedSet = if (wantGrant) grantedSet + app.packageName else grantedSet - app.packageName
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    if (wantGrant) HuijuiDeviceBridge.grantRootToApp(context, app.packageName)
                                    else HuijuiDeviceBridge.revokeRootFromApp(context, app.packageName)
                                }
                                if (success) {
                                    AppSettings.grantedApps = if (wantGrant) AppSettings.grantedApps + app.packageName else AppSettings.grantedApps - app.packageName
                                    AppSettings.save()
                                    Toast.makeText(context, zakoString("已更新底层授权"), Toast.LENGTH_SHORT).show()
                                } else {
                                    grantedSet = oldSet
                                    Toast.makeText(context, zakoString("写入失败，底层通道不可用"), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagerStatusCard(manager: RootManager?, grantedCount: Int) {
    val managerName = manager?.displayName ?: "检测中…"
    val isNoRoot = manager == RootManager.NONE
    val statusColor = if (isNoRoot) Color(0xFFEF4444) else Color(0xFF81C784)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Security, null, tint = statusColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText("当前 Root 管理器", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    ZakoText(if (isNoRoot) "连 Root 都没有的废柴" else "$managerName 打工中", color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!isNoRoot) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        ZakoText("兼容模式", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val accent = AppSettings.accentColor.color
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.08f)).border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
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
                Icon(Icons.Default.Clear, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onClear() })
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfoItem, isGranted: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color

    val borderColor by animateColorAsState(if (isGranted) accent.copy(alpha = 0.4f) else Color.White.copy(0.08f), label = "appRowBorder")

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onToggle(!isGranted) }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).border(1.dp, borderColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = remember(app.packageName) {
                        try {
                            context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
                        } catch (e: Exception) {
                            android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                        }
                    },
                    contentDescription = app.appName,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(app.appName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                ZakoText(app.packageName, color = Color.White.copy(0.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = isGranted,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent, uncheckedThumbColor = Color.White.copy(0.5f), uncheckedTrackColor = Color.White.copy(0.1f), uncheckedBorderColor = Color.White.copy(0.2f))
            )
        }
    }
}
