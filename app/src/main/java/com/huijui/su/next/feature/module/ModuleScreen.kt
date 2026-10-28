package com.huijui.su.next.feature.module
import com.huijui.su.next.core.ui.language.ZakoText

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
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
import com.huijui.su.next.core.root.HuijuiModuleBridge
import com.huijui.su.next.core.root.HuijuiModuleInstaller
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.launch

@Composable
fun ModuleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modules by remember { mutableStateOf<List<String>>(emptyList()) }
    var isInstalling by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    // 定时拉取模块列表
    LaunchedEffect(Unit) {
        while (true) {
            modules = HuijuiModuleBridge.getMountedModules()
            kotlinx.coroutines.delay(5000)
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                isInstalling = true
                progress = 0
                val success = HuijuiModuleInstaller.installModule(context, uri) { p ->
                    progress = p
                }
                isInstalling = false
                if (success) {
                    // 刷新列表
                    modules = HuijuiModuleBridge.getMountedModules()
                    android.widget.Toast.makeText(context, "✅ 模块安装并热挂载成功！", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "❌ 安装失败，请检查模块文件", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // UI 呼吸动画值
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // 头部区域
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                ZakoText("模块管理", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                ZakoText("底层热挂载 · 零重启体验", color = Color.White.copy(0.6f), fontSize = 12.sp)
            }
            
            // 呼吸指示灯
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(breathScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.radialGradient(listOf(AppSettings.accentColor.color, Color.Transparent)))
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // 安装进度条（如果正在安装）
        if (isInstalling) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ZakoText("正在解析并热挂载模块...", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = AppSettings.accentColor.color,
                        trackColor = Color.White.copy(0.1f)
                    )
                }
            }
        }

        // 模块列表
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(modules) { modName ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 左侧图标光晕
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(AppSettings.accentColor.color.copy(0.4f), AppSettings.accentColor.color.copy(0.1f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Extension, null, tint = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(modName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                ZakoText("已加载 · OverlayFS", color = Color(0xFF81C784), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 底部安装按钮
            item {
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = { if (!isInstalling) zipPicker.launch("application/zip") }
                ) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        ZakoText("选择本地 ZIP 模块安装", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
