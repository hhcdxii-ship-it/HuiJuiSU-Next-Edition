package com.huijui.sunext.edition.feature.toolbox
import com.huijui.sunext.edition.core.ui.language.ZakoText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HuijuiModuleBridge
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuijuiConsoleScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kernelLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var modules by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPerfMode by remember { mutableStateOf(false) }
    var currentGov by remember { mutableStateOf("schedutil") }

    // 定时刷新日志
    LaunchedEffect(Unit) {
        while (true) {
            kernelLogs = HuijuiModuleBridge.getKernelLogs()
            modules = HuijuiModuleBridge.getMountedModules()
            kotlinx.coroutines.delay(3000) // 每 3 秒刷新一次
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZakoText("HUI 内核控制台", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 性能调优区
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZakoText("⚡ HUI 独占性能调优", color = AppSettings.accentColor.color, fontWeight = FontWeight.Bold)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZakoText("强制性能模式", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isPerfMode,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    if (HuijuiModuleBridge.setPerformanceMode(checked)) isPerfMode = checked
                                }
                            }
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZakoText("CPU 调度器", color = Color.White, modifier = Modifier.weight(1f))
                        ZakoText(currentGov, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // 2. 模块列表区
            ZakoText("已加载模块 (${modules.size})", color = Color.White.copy(0.7f))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                if (modules.isEmpty()) {
                    ZakoText("暂无已挂载模块", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    modules.forEach {
                        ZakoText("- $it", color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(4.dp))
                    }
                }
            }

            // 3. 纯血终端日志区
            ZakoText("Kernel Logs (实时)", color = Color.White.copy(0.7f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.Black.copy(0.8f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                LazyColumn {
                    items(kernelLogs) { log ->
                        ZakoText(
                            text = log,
                            color = Color(0xFF00FF00), // 极客绿
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
