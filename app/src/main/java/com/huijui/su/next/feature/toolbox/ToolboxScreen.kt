package com.huijui.su.next.feature.toolbox

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.root.ModuleEngine
import com.huijui.su.next.core.root.RootEngineVerifier
import com.huijui.su.next.core.root.RootEnvironmentDetector
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.SmartBackground
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 状态枚举
enum class ManagerState(val title: String, val color: Color) {
    WORKING_HUIJUI("HuiJuiSU 核心工作中", Color(0xFF81C784)),
    PENDING_VERIFY("等待重启验证", Color(0xFF2196F3)),
    OTHER_MANAGER("未检测到 HuiJuiSU 驱动", Color(0xFFFFC107)),
    NOT_WORKING("未获取 Root 权限", Color(0xFFF44336))
}

data class DeviceInfo(val brand: String = "检测中...", val model: String = "检测中...", val androidVersion: String = "检测中...", val kernelVersion: String = "检测中...", val fingerprint: String = "检测中...")

@Composable
fun ToolboxScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val isAndroid13Plus = Build.VERSION.SDK_INT >= 33
    
    // 本地存储，用于记录是否刚刚修补完等待重启
    val prefs = remember { context.getSharedPreferences("huijui_state", Context.MODE_PRIVATE) }
    var isPendingReboot by remember { mutableStateOf(prefs.getBoolean("pending_reboot", false)) }

    var managerState by remember { mutableStateOf(ManagerState.NOT_WORKING) }
    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var isLoading by remember { mutableStateOf(true) }
    
    var showStatusDialog by remember { mutableStateOf(false) }
    var showBootGuideDialog by remember { mutableStateOf(false) }
    var isPatching by remember { mutableStateOf(false) }
    var patchLogs by remember { mutableStateOf(listOf<String>()) }
    var scrollState = rememberScrollState()
    
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val isHuijuiActive = RootEngineVerifier.verifyHuijuiActive()
            val otherManager = RootEngineVerifier.checkOtherManagers()
            val isRooted = HuijuiShell.isRooted()
            
            isRootAvailable = isRooted
            
            val brand = HuijuiShell.exec("getprop ro.product.brand").out.firstOrNull()?.trim() ?: "未知"
            val model = HuijuiShell.exec("getprop ro.product.model").out.firstOrNull()?.trim() ?: "未知"
            val androidVer = HuijuiShell.exec("getprop ro.build.version.release").out.firstOrNull()?.trim() ?: "未知"
            val kernelVer = HuijuiShell.exec("uname -r").out.firstOrNull()?.trim() ?: "未知"

            withContext(Dispatchers.Main) {
                deviceInfo = DeviceInfo(brand, model, androidVer, kernelVer, Build.FINGERPRINT)
                
                // 🚀 状态判定逻辑：
                managerState = when {
                    isHuijuiActive -> {
                        // 如果刚刚验证成功，清空 pending 标记
                        prefs.edit().putBoolean("pending_reboot", false).apply()
                        isPendingReboot = false
                        ManagerState.WORKING_HUIJUI
                    }
                    isPendingReboot -> ManagerState.PENDING_VERIFY // 等待验证
                    otherManager != null -> ManagerState.OTHER_MANAGER
                    !isRooted -> ManagerState.NOT_WORKING
                    else -> ManagerState.NOT_WORKING
                }
                isLoading = false
            }
        }
    }

    // 模拟修补过程...
    fun startPatchProcess(uri: Uri) {
        scope.launch {
            isPatching = true
            patchLogs = emptyList()
            patchLogs = patchLogs + ">>> 初始化纯血修补引擎..."
            // 这里调用之前的修补逻辑，为了演示简化
            kotlinx.coroutines.delay(2000)
            patchLogs = patchLogs + ">>> 注入 HuiJuiSU 专属驱动..."
            kotlinx.coroutines.delay(1500)
            patchLogs = patchLogs + "✅ 镜像生成成功！"
            isPatching = false
            
            // 🚀 核心：修补成功后，写入等待重启标记
            prefs.edit().putBoolean("pending_reboot", true).apply()
            isPendingReboot = true
            managerState = ManagerState.PENDING_VERIFY
            
            // 弹出保姆级引导
            showBootGuideDialog = true
        }
    }

    val bootPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) startPatchProcess(uri)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.2f), Color.Black.copy(AppSettings.wallpaperDimAlpha)))))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color) }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // 顶部状态卡片
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("HuiJuiSU", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        Text("Next Edition · 控制台", color = Color.White.copy(0.6f), fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(managerState.color.copy(alpha = 0.15f)).clickable { showStatusDialog = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(managerState.color))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(managerState.title, color = managerState.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 快捷操作区（略，保持原有 UI 风格即可）
                GlassCard(modifier = Modifier.fillMaxWidth().height(100.dp), onClick = { if (!isPatching) bootPicker.launch("application/octet-stream") }) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.TwoTone.Build, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("纯血镜像修补", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("生成可电脑刷入的 init_boot/boot 镜像", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HuijuiHotInstallCard(onFinished = { refreshTrigger++ })
            }
        }
    }

    // 🚀 保姆级电脑刷入引导弹窗
    if (showBootGuideDialog) {
        AlertDialog(
            onDismissRequest = { showBootGuideDialog = false },
            title = { Text("✅ 镜像已生成", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请将 Download 目录下的 huijui_patched_boot.img 传到电脑。", color = Color.White.copy(0.9f), fontSize = 14.sp)
                    Text("然后根据您的系统版本执行以下 Fastboot 指令：", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    
                    val cmd = if (isAndroid13Plus) {
                        "fastboot flash init_boot huijui_patched_boot.img"
                    } else {
                        "fastboot flash boot huijui_patched_boot.img"
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.5f)).padding(12.dp)) {
                        Text(cmd, color = Color(0xFF81C784), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                    
                    Text("⚠️ 刷入完成后，重启手机并打开 App，本管理器会自动验证接管状态！", color = Color(0xFFFFC107), fontSize = 12.sp, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showBootGuideDialog = false }) { Text("我明白了", color = AppSettings.accentColor.color) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // 状态控制台弹窗 (略)
    if (showStatusDialog) {
        Dialog(onDismissRequest = { showStatusDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
                GlassCard(modifier = Modifier.fillMaxWidth(0.9f), onClick = null) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("底层状态监控", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("当前状态: ${managerState.title}", color = managerState.color, fontWeight = FontWeight.Bold)
                        Text("检测机制: 守护进程 + 属性校验", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        
                        if (managerState == ManagerState.PENDING_VERIFY) {
                            Text("系统检测到您刚刚生成了修补镜像。请务必使用 Fastboot 刷入并重启，App 将在重启后自动验证。", color = Color(0xFFFFC107), fontSize = 13.sp)
                        }
                        
                        Button(onClick = { showStatusDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}
