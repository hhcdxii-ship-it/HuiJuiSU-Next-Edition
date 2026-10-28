package com.huijui.su.next.feature.module

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.VolumeUp
import androidx.compose.material.icons.twotone.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.component.GlassCard
import kotlinx.coroutines.delay

enum class InstallState { INSTALLING, WAITING_INPUT, SUCCESS, FAILED }

@Composable
fun ModuleInstallDialog(
    fileName: String,
    onDismiss: () -> Unit
) {
    var currentLog by remember { mutableStateOf("准备安装...") }
    var installState by remember { mutableStateOf(InstallState.INSTALLING) }
    var showVolumePrompt by remember { mutableStateOf(false) }
    
    // 呼吸灯动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
    )

    // 模拟底层安装流程 (实际中这里接入 HuijuiShell 的日志流监听)
    LaunchedEffect(Unit) {
        val fakeLogs = listOf(
            "解压模块文件...",
            "检查模块结构...",
            "执行安装脚本...",
            "Press Vol+ to confirm, Vol- to cancel", // 模拟触发音量键交互
            "等待用户选择..."
        )
        
        for (i in fakeLogs.indices) {
            delay(1200)
            val log = fakeLogs[i]
            // 过滤无用日志，只展示人类能看懂的
            if (log.contains("Press Vol") || log.contains("等待")) {
                currentLog = "模块需要您的确认选择"
                installState = InstallState.WAITING_INPUT
                showVolumePrompt = true
            } else {
                currentLog = log
            }
        }
        
        // 模拟用户点击确认后，继续执行成功
        delay(3000)
        if (installState == InstallState.WAITING_INPUT) {
            // 假如用户没点，一直等
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // 拦截背景点击
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            onClick = null
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                // 文件图标 (用渐变色模拟)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF81C784), Color(0xFF4CAF50))))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ZIP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(fileName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                Spacer(modifier = Modifier.height(24.dp))

                // 状态展示区
                if (installState == InstallState.INSTALLING) {
                    CircularProgressIndicator(color = Color(0xFF81C784), strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                } else if (installState == InstallState.SUCCESS) {
                    Icon(Icons.TwoTone.CheckCircle, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(48.dp))
                } else if (installState == InstallState.FAILED) {
                    Icon(Icons.TwoTone.Error, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(48.dp))
                } else if (installState == InstallState.WAITING_INPUT) {
                    Icon(Icons.TwoTone.VolumeUp, contentDescription = null, tint = Color(0xFFFFC107).copy(alpha = pulseAlpha), modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(currentLog, color = Color.White.copy(0.8f), fontSize = 15.sp, fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.height(24.dp))

                // 🚀 音量键交互弹出面板 (丝滑入场)
                AnimatedVisibility(
                    visible = showVolumePrompt,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("请选择操作：", color = Color.White.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 音量加 (确认)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF4CAF50).copy(0.2f))
                                    .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(16.dp))
                                    .clickable {
                                        // 🚀 底层发送 KEYCODE_VOLUME_UP (24)
                                        // HuijuiShell.exec("input keyevent 24")
                                        showVolumePrompt = false
                                        installState = InstallState.INSTALLING
                                        currentLog = "正在完成安装..."
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.TwoTone.VolumeUp, null, tint = Color(0xFF4CAF50))
                                    Spacer(Modifier.width(8.dp))
                                    Text("音量+ (确认)", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                            }

                            // 音量减 (取消)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF44336).copy(0.2f))
                                    .border(1.dp, Color(0xFFF44336), RoundedCornerShape(16.dp))
                                    .clickable {
                                        // 🚀 底层发送 KEYCODE_VOLUME_DOWN (25)
                                        // HuijuiShell.exec("input keyevent 25")
                                        showVolumePrompt = false
                                        installState = InstallState.FAILED
                                        currentLog = "用户已取消安装"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.TwoTone.VolumeDown, null, tint = Color(0xFFF44336))
                                    Spacer(Modifier.width(8.dp))
                                    Text("音量- (取消)", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 退出/确定按钮 (仅在成功或失败时显示)
                if (installState == InstallState.SUCCESS || installState == InstallState.FAILED) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("完成", color = Color.White)
                    }
                }
            }
        }
    }
}
