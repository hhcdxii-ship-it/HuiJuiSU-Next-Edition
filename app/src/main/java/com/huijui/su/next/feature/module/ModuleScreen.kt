package com.huijui.su.next.feature.module
import com.huijui.su.next.core.utils.AppSettings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.HuijuiModuleInfo
import com.huijui.su.next.core.root.ModuleEngine
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.SmartBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModuleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var modules by remember { mutableStateOf<List<HuijuiModuleInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    // 刷入状态
    var showInstallDialog by remember { mutableStateOf(false) }
    var installFileName by remember { mutableStateOf("") }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val enterAlpha by animateFloatAsState(if (isVisible) 1f else 0f, tween(600), label = "enter")

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathAlpha by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "breath")

    fun refreshModules() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    modules = ModuleEngine.getInstalledModules()
                } catch (e: Exception) {
                    errorMsg = "读取模块失败: ${e.message}"
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshModules() }

    // 文件选择器
    val filePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            installFileName = getFileNameFromUri(context, uri)
            showInstallDialog = true // 🚀 弹出极简控制台
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(AppSettings.wallpaperDimAlpha)))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).scale(enterAlpha)) {
            Spacer(modifier = Modifier.height(60.dp))
            Text("模块", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("本地模块管理与扩展", color = Color.White.copy(0.6f), fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(24.dp))

            // 操作面板
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                GlassCard(
                    modifier = Modifier.weight(1f).height(80.dp),
                    onClick = { Toast.makeText(context, "正在连接在线模块仓库...", Toast.LENGTH_SHORT).show() }
                ) {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.TwoTone.Cloud, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("模块仓库", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                GlassCard(
                    modifier = Modifier.weight(1f).height(80.dp),
                    onClick = { filePicker.launch("application/zip") }
                ) {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.TwoTone.SystemUpdate, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("刷入模块", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 模块列表
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
            } else if (errorMsg != null) {
                Text(errorMsg!!, color = Color.Red, fontSize = 14.sp)
            } else if (modules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无已装载模块", color = Color.White.copy(0.5f), fontSize = 16.sp) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(modules) { module ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(AppSettings.accentColor.color.copy(alpha = 0.2f * breathAlpha)))
                                    Icon(Icons.TwoTone.Extension, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(module.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("版本: ${module.version} | 作者: ${module.author}", color = Color.White.copy(0.6f), fontSize = 12.sp)
                                }
                                Text("已启用", color = AppSettings.accentColor.color.copy(alpha = breathAlpha), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 🚀 接入全屏沉浸式极简控制台
    if (showInstallDialog) {
        ModuleInstallDialog(
            fileName = installFileName,
            onDismiss = { 
                showInstallDialog = false
                refreshModules() // 装完刷新列表
            }
        )
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var fileName = "unknown.zip"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) { val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (displayNameIndex != -1) fileName = cursor.getString(displayNameIndex) } }
    return fileName
}
