package com.huijui.sunext.edition.feature.module

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huijui.sunext.edition.core.root.*
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.language.zakoString
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ModuleTab { LOCAL, ONLINE }

@Composable
fun ModuleScreen(onNavigateToWebUI: (name: String, path: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var currentTab by remember { mutableStateOf(ModuleTab.LOCAL) }
    var isPureHui by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val manager = RootManagerDetector.detect(forceRefresh = true)
        // 🛡️ 双重防伪装：不仅名字要是 HUI，而且底层绝对不能有 ksud 存在！
        val hasKsu = KsuBridge.isKsuAvailable()
        isPureHui = (manager == RootManager.HUIJUI || manager == RootManager.HUIJUI_ULTRA) && !hasKsu
    }

    // Local state
    var installedModules by remember { mutableStateOf<List<InstalledModule>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }
    
    // Online state
    var remoteModules by remember { mutableStateOf<List<ModuleRepoProvider.RemoteModule>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Detail state
    var selectedModuleDetail by remember { mutableStateOf<ModuleRepoProvider.RemoteModule?>(null) }

    suspend fun loadLocalModules() {
        isLoadingLocal = true; localError = null
        try {
            val result = withContext(Dispatchers.IO) { ModuleManager.scanModules() }
            installedModules = result
            if (result.isEmpty()) localError = "没读到模块"
        } catch (e: Exception) { localError = "底层报错: ${e.message}" } finally { isLoadingLocal = false }
    }

    suspend fun fetchRemoteModules() {
        isLoadingRemote = true; remoteError = null
        try {
            val list = withContext(Dispatchers.IO) { ModuleRepoProvider.fetchModules() }
            if (list.isEmpty()) remoteError = "仓库里空空如也" else remoteModules = list.sortedByDescending { it.downloadCount }
        } catch (e: Exception) { remoteError = "断网啦废物！" } finally { isLoadingRemote = false }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == ModuleTab.LOCAL) loadLocalModules() else { if (remoteModules.isEmpty()) fetchRemoteModules() }
    }

    // 🚀 文件选择器（纯血安装 ZIP）
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(it) }
                    val tempFile = File(context.cacheDir, "temp_module.zip")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(tempFile).use { output -> inputStream?.copyTo(output) }
                    }
                    inputStream?.close()
                    
                    Toast.makeText(context, "正在安装模块...", Toast.LENGTH_SHORT).show()
                    
                    // 纯血 UI 安装：这里应该触发专门的安装 Dialog，但为了快速跑通：
                    val success = ModuleManager.installModule(tempFile.absolutePath)
                    
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "模块安装完成", Toast.LENGTH_LONG).show()
                            loadLocalModules()
                        } else {
                            Toast.makeText(context, "底层拦截：纯血模块请通过专属弹窗安装", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZakoText("模块拓展", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                
                // 🛡️ 只有纯血 HUI 才显示导入按钮
                if (currentTab == ModuleTab.LOCAL && isPureHui) {
                    IconButton(onClick = { zipPickerLauncher.launch("application/zip") }) {
                        Icon(Icons.Default.Add, "导入模块", tint = Color.White)
                    }
                }
                
                IconButton(onClick = { scope.launch { if (currentTab == ModuleTab.LOCAL) loadLocalModules() else { remoteModules = emptyList(); fetchRemoteModules() } } }) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Tab
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.06f)).padding(4.dp)) {
                listOf(ModuleTab.LOCAL to "本地已装", ModuleTab.ONLINE to "在线仓库").forEach { (tab, label) ->
                    val selected = currentTab == tab
                    val bgColor = if (selected) AppSettings.accentColor.color.copy(alpha = 0.3f) else Color.Transparent
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bgColor).clickable { currentTab = tab }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        ZakoText(label, color = if (selected) AppSettings.accentColor.color else Color.White.copy(0.5f), fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (currentTab == ModuleTab.LOCAL) {
                if (isLoadingLocal) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
                } else if (installedModules.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ZakoText(localError ?: "一个模块都没有", color = Color(0xFFEF4444), fontSize = 14.sp) }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(installedModules, key = { it.id }) { module ->
                            LocalModuleCard(module,
                                onToggle = { enable -> 
                                    scope.launch { 
                                        if (ModuleManager.toggleModule(module.id, enable)) {
                                            loadLocalModules()
                                        } else {
                                            Toast.makeText(context, "操作失败：纯血模式锁", Toast.LENGTH_SHORT).show()
                                        }
                                    } 
                                },
                                onUninstall = { 
                                    scope.launch { 
                                        if (ModuleManager.uninstallModule(module.id)) {
                                            loadLocalModules()
                                        } else {
                                            Toast.makeText(context, "卸载被拦截：纯血模式锁", Toast.LENGTH_SHORT).show()
                                        }
                                    } 
                                }
                            )
                        }
                    }
                }
            } else {
                // 在线仓库
                if (isLoadingRemote) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
                } else if (remoteError != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ZakoText(remoteError!!, color = Color(0xFFEF4444), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            GlassCard(modifier = Modifier.wrapContentSize(), onClick = { scope.launch { fetchRemoteModules() } }) {
                                Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { ZakoText("重新拉取", color = AppSettings.accentColor.color, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.08f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(AppSettings.accentColor.color),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) ZakoText("搜索模块...", color = Color.White.copy(0.3f), fontSize = 14.sp)
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(Icons.Default.Clear, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp).clickable { searchQuery = "" })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val filteredList = remoteModules.filter { it.name.contains(searchQuery, true) || it.id.contains(searchQuery, true) || it.author.contains(searchQuery, true) }
                    if (filteredList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ZakoText("没找到匹配的应用", color = Color.White.copy(0.5f)) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredList, key = { it.id }) { m ->
                                Box(modifier = Modifier.clickable { selectedModuleDetail = m }) {
                                    RemoteModuleCard(module = m)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 🚨 右下角兼容模式警告
        if (currentTab == ModuleTab.LOCAL && !isPureHui) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 24.dp) 
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                ZakoText(
                    text = "兼容模式下禁止插拔模块，去切回纯血 HUI 啦！",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    selectedModuleDetail?.let { module ->
        ModuleDetailDialog(module = module, onDismiss = { selectedModuleDetail = null })
    }
}

@Composable
fun ModuleDetailDialog(module: ModuleRepoProvider.RemoteModule, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                ZakoText(module.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                ZakoText("作者: ${module.author} | 下载量: ${module.downloadCount}", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                ZakoText("介绍", color = AppSettings.accentColor.color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                ZakoText(module.description.ifBlank { "无描述" }, color = Color.White.copy(0.8f), fontSize = 13.sp, modifier = Modifier.heightIn(max = 120.dp).weight(1f, false))
                Spacer(modifier = Modifier.height(16.dp))
                ZakoText("历史版本", color = AppSettings.accentColor.color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(module.releases) { rel ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            ZakoText(rel.version, color = Color.White, fontSize = 13.sp)
                            ZakoText("${rel.size / 1024 / 1024} MB", color = Color.White.copy(0.5f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalModuleCard(module: InstalledModule, onToggle: (Boolean) -> Unit, onUninstall: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF64B5F6).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Extension, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText(module.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ZakoText("v${module.version} · ${module.author}", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
                Switch(checked = module.isEnabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = AppSettings.accentColor.color))
            }
            Spacer(modifier = Modifier.height(8.dp))
            ZakoText(module.description, color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (module.removePending) ZakoText("等待卸载", color = Color(0xFFEF4444), fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF4444).copy(alpha = 0.15f)).clickable { onUninstall() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ZakoText("卸载", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RemoteModuleCard(module: ModuleRepoProvider.RemoteModule) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0EA5B7).copy(0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF0EA5B7), modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText(module.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ZakoText("v${module.version} · ${module.downloadCount} 次下载", color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ZakoText(module.description ?: "无描述", color = Color.White.copy(0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
