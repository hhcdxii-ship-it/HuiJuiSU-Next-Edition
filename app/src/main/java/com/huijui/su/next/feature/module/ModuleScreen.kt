package com.huijui.su.next.feature.module

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.HuijuiModuleUninstaller
import com.huijui.su.next.core.root.ModuleListProvider
import com.huijui.su.next.core.root.ModuleRepoProvider
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ModuleTab { LOCAL, ONLINE }

// 🚀 镜像前缀
private val GITHUB_MIRRORS = listOf("https://ghfast.top/", "https://gh-proxy.com/", "https://ghproxy.net/")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(onNavigateToWebUI: (name: String, path: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(ModuleTab.LOCAL) }
    
    // 本地状态
    var huijuiModules by remember { mutableStateOf<List<ModuleListProvider.ModuleEntry>>(emptyList()) }
    var externalModules by remember { mutableStateOf<List<ModuleListProvider.ModuleEntry>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInstallName by remember { mutableStateOf("") }
    var selectedModule by remember { mutableStateOf<ModuleListProvider.ModuleEntry?>(null) }
    var pendingUninstallId by remember { mutableStateOf<String?>(null) }
    
    // 在线状态
    var remoteModules by remember { mutableStateOf<List<ModuleRepoProvider.RemoteModule>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var selectedRemoteType by remember { mutableStateOf("all") }
    
    // 下载与更新状态
    var downloadingModule by remember { mutableStateOf<Any?>(null) } // 可以是 RemoteModule 或 ModuleEntry
    var downloadProgress by remember { mutableStateOf(0f) }
    
    // 🚀 更新检测状态：Map<本地模块名(小写), 远程模块数据>
    var updateAvailableMap by remember { mutableStateOf<Map<String, ModuleRepoProvider.RemoteModule>>(emptyMap()) }

    val zipPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingInstallUri = it; pendingInstallName = queryFileName(context, it) ?: "module.zip" }
    }

    // 拉取本地模块
    suspend fun reloadLocal() {
        isLoadingLocal = true
        withContext(Dispatchers.IO) {
            val all = ModuleListProvider.loadAll()
            withContext(Dispatchers.Main) {
                huijuiModules = all.filter { it.isHuijuiModule }
                externalModules = all.filterNot { it.isHuijuiModule }
                isLoadingLocal = false
            }
        }
    }

    // 拉取远程模块
    suspend fun fetchRemoteModules() {
        if (remoteModules.isNotEmpty()) return
        isLoadingRemote = true
        remoteError = null
        val list = withContext(Dispatchers.IO) { ModuleRepoProvider.fetchModules() }
        if (list.isEmpty()) remoteError = "拉取失败，请检查网络或源地址"
        else remoteModules = list.sortedByDescending { it.downloadCount }
        isLoadingRemote = false
    }

    // 初始化加载
    LaunchedEffect(refreshTrigger) {
        if (currentTab == ModuleTab.LOCAL) reloadLocal()
    }

    // 当有两个列表时，检测更新
    LaunchedEffect(huijuiModules, externalModules, remoteModules) {
        if (remoteModules.isNotEmpty() && (huijuiModules.isNotEmpty() || externalModules.isNotEmpty())) {
            val localApps = (huijuiModules + externalModules)
            val updates = mutableMapOf<String, ModuleRepoProvider.RemoteModule>()
            
            for (local in localApps) {
                // 通过名称模糊匹配 (忽略大小写和连字符)
                val match = remoteModules.find { 
                    it.name.lowercase().replace("-", "") == local.name.lowercase().replace("-", "") || 
                    it.id.lowercase().contains(local.id.lowercase())
                }
                if (match != null) {
                    // 比对版本 (简单比对：如果不完全相同，且本地版本不包含远程版本，则视为有更新)
                    val remoteVer = match.version.lowercase().trimStart('v')
                    val localVer = local.version.lowercase().trimStart('v')
                    if (remoteVer != localVer && remoteVer > localVer) {
                        updates[local.name.lowercase()] = match
                    }
                }
            }
            updateAvailableMap = updates
        }
    }

    // 下载与安装逻辑
    fun startDownloadAndInstall(zipUrl: String, name: String, version: String) {
        scope.launch {
            downloadingModule = name
            downloadProgress = 0f

            val candidateUrls = mutableListOf(zipUrl)
            GITHUB_MIRRORS.forEach { prefix -> candidateUrls.add(prefix + zipUrl) }

            var successFile: File? = null
            for (urlStr in candidateUrls) {
                if (successFile != null) break
                val resultFile = withContext(Dispatchers.IO) {
                    try {
                        val url = URL(urlStr)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 15000
                        conn.connect()
                        if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

                        val totalSize = conn.contentLength.toFloat()
                        val file = File(context.cacheDir, "${name}_${version}.zip")
                        conn.inputStream.use { input ->
                            FileOutputStream(file).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalRead = 0L
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                    if (totalSize > 0) downloadProgress = totalRead / totalSize
                                }
                            }
                        }
                        conn.disconnect()
                        file
                    } catch (e: Exception) { null }
                }
                if (resultFile != null && resultFile.exists() && resultFile.length() > 0) successFile = resultFile
            }

            if (successFile != null) {
                pendingInstallUri = Uri.fromFile(successFile)
                pendingInstallName = "${name}_${version}.zip"
            } else {
                Toast.makeText(context, "下载失败，直连与所有镜像均不可用", Toast.LENGTH_LONG).show()
            }
            downloadingModule = null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (currentTab == ModuleTab.LOCAL) {
                FloatingActionButton(onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, containerColor = AppSettings.accentColor.color) {
                    Icon(Icons.Default.Add, "安装", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                Text("模块拓展", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { 
                    if (currentTab == ModuleTab.LOCAL) refreshTrigger++ 
                    else { remoteModules = emptyList(); scope.launch { fetchRemoteModules() } } 
                }) { Icon(Icons.Default.Refresh, null, tint = Color.White.copy(0.8f)) }
            }

            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.06f)).padding(4.dp)) {
                listOf(ModuleTab.LOCAL to "本地已装", ModuleTab.ONLINE to "在线仓库").forEach { (tab, label) ->
                    val selected = currentTab == tab
                    val bgColor by animateColorAsState(if (selected) AppSettings.accentColor.color.copy(alpha = 0.3f) else Color.Transparent, tween(200), label = "tabBg")
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bgColor).clickable { currentTab = tab }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (selected) AppSettings.accentColor.color else Color.White.copy(0.5f), fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentTab == ModuleTab.LOCAL) {
                if (isLoadingLocal) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 100.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (huijuiModules.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) { Text("HUI 专属", color = AppSettings.accentColor.color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) }
                            items(huijuiModules, key = { it.id }) { m -> 
                                val hasUpdate = updateAvailableMap.containsKey(m.name.lowercase())
                                ModuleTile(m, hasUpdate) { selectedModule = m } 
                            }
                        }
                        if (externalModules.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) { Text("外部加载", color = Color(0xFF64B5F6), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
                            items(externalModules, key = { it.id }) { m -> 
                                val hasUpdate = updateAvailableMap.containsKey(m.name.lowercase())
                                ModuleTile(m, hasUpdate) { selectedModule = m } 
                            }
                        }
                    }
                }
                // 后台悄悄拉取一次远程数据以检测更新
                LaunchedEffect(Unit) { fetchRemoteModules() }
            } else {
                if (isLoadingRemote) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
                } else if (remoteError != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(remoteError!!, color = Color(0xFFEF4444)) }
                } else {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "全部", "standard" to "标准", "zygisk" to "Zygisk", "lsposed" to "LSPosed", "metamodule" to "Meta").forEach { (type, label) ->
                                val isSelected = selectedRemoteType == type
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (isSelected) AppSettings.accentColor.color.copy(0.3f) else Color.White.copy(0.06f)).clickable { selectedRemoteType = type }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        val filteredRemote = if (selectedRemoteType == "all") remoteModules else if (selectedRemoteType == "metamodule") remoteModules.filter { it.moduleType == "metamodule" || it.moduleType == "hybrid_mount" } else remoteModules.filter { it.moduleType == selectedRemoteType }

                        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 100.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredRemote, key = { it.id }) { m ->
                                val isDownloading = downloadingModule == m.name
                                RemoteModuleTile(m, isDownloading, downloadProgress) { startDownloadAndInstall(m.zipUrl, m.name, m.version) }
                            }
                        }
                    }
                }
            }
        }
    }

    // 本地模块详情弹窗 (带更新检测)
    if (selectedModule != null) {
        val m = selectedModule!!
        val updateData = updateAvailableMap[m.name.lowercase()]
        
        ModalBottomSheet(onDismissRequest = { selectedModule = null }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("当前 v${m.version}", color = Color.White.copy(0.4f), fontSize = 12.sp)
                }
                
                // 🚀 发现更新提示
                if (updateData != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEF4444).copy(alpha = 0.15f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NewReleases, null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("发现新版本 v${updateData.version}", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("仓库已发布最新版本，建议立即更新", color = Color.White.copy(0.6f), fontSize = 11.sp)
                        }
                    }
                }

                Text(m.description, color = Color.White.copy(0.7f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
                
                if (updateData != null) {
                    Button(
                        onClick = { 
                            selectedModule = null
                            startDownloadAndInstall(updateData.zipUrl, updateData.name, updateData.version) 
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.SystemUpdate, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即更新至 v${updateData.version}", color = Color.White)
                    }
                }

                if (m.hasWebUi) {
                    Button(onClick = { selectedModule = null; onNavigateToWebUI(m.name, m.webUiPath) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Language, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("启动 WebUI 控制面板")
                    }
                }
                if (m.isHuijuiModule) {
                    OutlinedButton(onClick = { selectedModule = null; pendingUninstallId = m.id }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))) { Text("卸载此模块") }
                }
            }
        }
    }

    // 安装弹窗
    if (pendingInstallUri != null) {
        ModuleInstallDialog(pendingInstallName, pendingInstallUri!!, onDismiss = { pendingInstallUri = null; refreshTrigger++ })
    }
}

// ==========================================
// 🧩 卡片组件
// ==========================================
@Composable
private fun RemoteModuleTile(module: ModuleRepoProvider.RemoteModule, isDownloading: Boolean, progress: Float, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().height(130.dp), onClick = if (isDownloading) null else onClick) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF0EA5B7).copy(0.3f), Color(0xFF0EA5B7).copy(0.1f)))), contentAlignment = Alignment.Center) {
                    if (isDownloading) CircularProgressIndicator(progress = progress, color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    else Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF0EA5B7), modifier = Modifier.size(24.dp))
                }
                if (module.moduleType != "standard") {
                    val tagColor = when (module.moduleType) {
                        "lsposed" -> Color(0xFFA855F7)
                        "zygisk" -> Color(0xFF81C784)
                        "metamodule", "hybrid_mount" -> Color(0xFFEF4444)
                        else -> Color.Gray
                    }
                    Box(modifier = Modifier.offset(x = 4.dp, y = (-4).dp).clip(RoundedCornerShape(4.dp)).background(tagColor).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(module.moduleType.uppercase().take(4), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(module.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isDownloading) Text("下载中 ${(progress * 100).toInt()}%", color = AppSettings.accentColor.color, fontSize = 10.sp)
            else Text("${module.downloadCount} 次下载", color = Color.White.copy(0.4f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ModuleTile(module: ModuleListProvider.ModuleEntry, hasUpdate: Boolean, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().height(130.dp), onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AppSettings.accentColor.color.copy(0.3f), AppSettings.accentColor.color.copy(0.1f)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Extension, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                if (module.hasWebUi) { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF0EA5B7)).align(Alignment.TopEnd)) }
                
                // 🚀 更新角标
                if (hasUpdate) {
                    Box(modifier = Modifier.offset(x = 6.dp, y = (-6).dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEF4444)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("UP", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(module.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("v${module.version}", color = Color.White.copy(0.4f), fontSize = 10.sp)
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)) else null }
    } catch (_: Exception) { null }
}
