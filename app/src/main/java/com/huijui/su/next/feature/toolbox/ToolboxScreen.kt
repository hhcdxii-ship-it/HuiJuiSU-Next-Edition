package com.huijui.su.next.feature.toolbox
import com.huijui.su.next.core.ui.language.ZakoText

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.DeveloperMode
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.root.RootEngineVerifier
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.SmartBackground
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class DeviceInfo(
    val brand: String = "加载中...",
    val model: String = "加载中...",
    val androidVer: String = "加载中...",
    val kernelVer: String = "加载中...",
    val fingerprint: String = "加载中...",
    val selinux: String = "加载中...",
    val seccomp: String = "加载中...",
    val hookType: String = "加载中...",
    val zygisk: String = "加载中...",
    val mountImpl: String = "加载中..."
)

enum class ManagerState(val title: String, val color: Color) {
    WORKING_HUIJUI("工作中", Color(0xFF81C784)),
    PENDING_VERIFY("等待验证", Color(0xFFFFC107)),
    OTHER_MANAGER("其他管理器", Color(0xFF64B5F6)),
    NOT_WORKING("未获取权限", Color(0xFFE57373))
}

@Composable
fun ToolboxScreen(onNavigateToConsole: (() -> Unit)? = null) {
    val context = LocalContext.current

    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var managerState by remember { mutableStateOf(ManagerState.NOT_WORKING) }
    var isLoading by remember { mutableStateOf(true) }
    var isPatching by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 🚀 新增：控制卡片入场的状态
    var animationTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val isHuijuiActive = RootEngineVerifier.verifyHuijuiActive()
            val otherManager = RootEngineVerifier.checkOtherManagers()
            val isRooted = HuijuiShell.isRooted()

            AppSettings.hasRoot = isRooted

            val brand = HuijuiShell.exec("getprop ro.product.brand").out.firstOrNull()?.trim() ?: "未知"
            val model = HuijuiShell.exec("getprop ro.product.model").out.firstOrNull()?.trim() ?: "未知"
            val androidVer = HuijuiShell.exec("getprop ro.build.version.release").out.firstOrNull()?.trim() ?: "未知"
            val kernelVer = HuijuiShell.exec("uname -r").out.firstOrNull()?.trim() ?: "未知"
            val fingerprint = HuijuiShell.exec("getprop ro.build.fingerprint").out.firstOrNull()?.trim() ?: "未知"
            val selinux = HuijuiShell.exec("getenforce").out.firstOrNull()?.trim() ?: "Unknown"

            withContext(Dispatchers.Main) {
                deviceInfo = DeviceInfo(
                    brand, model, androidVer, kernelVer, fingerprint, 
                    selinux, "过滤模式", "Tracepoint Syscall", "Zygisk Next", "Hybrid Mount"
                )
                managerState = when {
                    isHuijuiActive -> ManagerState.WORKING_HUIJUI
                    otherManager != null -> ManagerState.OTHER_MANAGER
                    !isRooted -> ManagerState.NOT_WORKING
                    else -> ManagerState.NOT_WORKING
                }
                isLoading = false
                delay(50) // 数据加载完后稍等一下，触发入场动画
                animationTrigger = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppSettings.accentColor.color)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // 0. 顶部标题
                AnimatedCard(index = 0, trigger = animationTrigger) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            ZakoText("HuiJuiSU NEXT Ultra", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                            ZakoText("Next Edition · 纯血控制台", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White.copy(0.5f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 1. 大状态卡片
                AnimatedCard(index = 1, trigger = animationTrigger) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = managerState.color, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(managerState.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF81C784).copy(0.3f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    ZakoText("HUI Core", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ZakoText("${AppSettings.grantedApps.size} 个超级用户，0 个模块", color = Color.White.copy(0.7f), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 系统指纹
                AnimatedCard(index = 2, trigger = animationTrigger) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ZakoText("系统指纹", color = Color.White.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(deviceInfo.fingerprint, color = Color.White.copy(0.9f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                AnimatedCard(index = 3, trigger = animationTrigger) {
                    ZakoText("版本信息", color = Color.White.copy(0.6f), fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 3. 版本信息
                AnimatedCard(index = 4, trigger = animationTrigger) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoRow(Icons.Default.PhoneAndroid, "设备", "${deviceInfo.brand} ${deviceInfo.model}")
                            InfoRow(Icons.Default.Build, "内核版本", deviceInfo.kernelVer)
                            InfoRow(Icons.Default.DeveloperMode, "Android 版本", deviceInfo.androidVer)
                            InfoRow(Icons.Default.Info, "内核驱动版本", "v4.1.0-unknown-dirty@HuiJuiSU_NEXT")
                            InfoRow(Icons.Default.Settings, "管理器版本", "vNext-Edition (Build 1)")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                AnimatedCard(index = 5, trigger = animationTrigger) {
                    ZakoText("状态信息", color = Color.White.copy(0.6f), fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 4. 状态信息
                AnimatedCard(index = 6, trigger = animationTrigger) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoRow(Icons.Default.Security, "SELinux 状态", deviceInfo.selinux)
                            InfoRow(Icons.Default.List, "Seccomp 状态", deviceInfo.seccomp)
                            InfoRow(Icons.Default.Person, "活跃管理器", "UID: 10381 (HuiJuiSU NEXT Ultra)")
                            InfoRow(Icons.Default.Build, "钩子类型", deviceInfo.hookType)
                            InfoRow(Icons.Default.Extension, "Zygisk 实现", deviceInfo.zygisk)
                            InfoRow(Icons.Default.Folder, "元模块实现", deviceInfo.mountImpl)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                AnimatedCard(index = 7, trigger = animationTrigger) {
                    ZakoText("快捷操作", color = Color.White.copy(0.6f), fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 5. 操作区
                AnimatedCard(index = 8, trigger = animationTrigger) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { Toast.makeText(context, "选择镜像", Toast.LENGTH_SHORT).show() }) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                ZakoText("纯血镜像修补", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                ZakoText("生成可电脑刷入的 init_boot/boot 镜像", color = Color.White.copy(0.6f), fontSize = 12.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedCard(index = 9, trigger = animationTrigger) {
                    HuijuiHotInstallCard(onFinished = { refreshTrigger++ })
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { 
                    onNavigateToConsole?.invoke() 
                }) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            ZakoText("HUI 内核控制台", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            ZakoText("查看底层日志与性能调度", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(160.dp))
            }
        }
    }
}

// 🚀 自定义错峰入场动画组件
@Composable
fun AnimatedCard(index: Int, trigger: Boolean, content: @Composable () -> Unit) {
    // 通过延迟和位移，实现自上而下的瀑布流效果
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger) {
            delay(index * 60L) // 每个卡片错开 60 毫秒
            visible = true
        }
    }
    
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "alpha")
    // 使用带弹性的 Spring 动画，让卡片滑入时有那种果冻感
    val offsetY by animateFloatAsState(if (visible) 0f else 80f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "offsetY")

    Box(modifier = Modifier.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY
    }) {
        content()
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White.copy(0.6f), fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
