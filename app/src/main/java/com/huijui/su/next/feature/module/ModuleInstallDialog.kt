package com.huijui.su.next.feature.module

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.FlashLogTranslator
import com.huijui.su.next.core.root.HuijuiModuleInstaller
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.rememberPausableInfiniteTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class InstallState { INSTALLING, SUCCESS, FAILED }

@Composable
fun ModuleInstallDialog(
    fileName: String,
    zipUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installState by remember { mutableStateOf(InstallState.INSTALLING) }
    var currentStage by remember { mutableStateOf("准备安装…") }
    var lastProgress by remember { mutableStateOf("") }
    var volumePromptMessage by remember { mutableStateOf<String?>(null) }

    // 🎨 活力动画
    val infinite = rememberPausableInfiniteTransition(label = "installEnergy")

    // 环形旋转（两个反向）
    val rotate1 by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "rotate1"
    )
    val rotate2 by infinite.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "rotate2"
    )

    // 呼吸光点
    val pulseAlpha by infinite.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // 图标呼吸
    val iconBreath by infinite.animateFloat(
        1f, 1.08f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconBreath"
    )

    // 成功弹跳
    val successScale = remember { Animatable(1f) }
    LaunchedEffect(installState) {
        if (installState == InstallState.SUCCESS) {
            successScale.animateTo(1.15f, spring(dampingRatio = 0.3f, stiffness = 500f))
            successScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        } else {
            successScale.snapTo(1f)
        }
    }

    // 成功光晕扩散
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(installState) {
        if (installState == InstallState.SUCCESS) {
            glowAlpha.animateTo(0.6f, tween(300))
            glowAlpha.animateTo(0f, tween(800))
        } else {
            glowAlpha.snapTo(0f)
        }
    }

    LaunchedEffect(Unit) {
        val success = HuijuiModuleInstaller.installModuleStreaming(
            context = context,
            zipUri = zipUri,
            onTranslated = { translated ->
                scope.launch(Dispatchers.Main) {
                    when (translated) {
                        is FlashLogTranslator.TranslatedLine.Text -> {
                            currentStage = translated.text
                            lastProgress = ""
                        }
                        is FlashLogTranslator.TranslatedLine.Progress -> lastProgress = translated.text
                        is FlashLogTranslator.TranslatedLine.Result -> currentStage = translated.message
                        else -> {}
                    }
                }
            },
            onVolumeRequest = { message ->
                scope.launch(Dispatchers.Main) { volumePromptMessage = message }
            }
        )

        withContext(Dispatchers.Main) {
            installState = if (success) InstallState.SUCCESS else InstallState.FAILED
            currentStage = if (success) "安装完成" else "安装失败"
            volumePromptMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                onClick = null
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    // 🌀 ZIP 图标 + 能量环
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 外环（顺时针）
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .graphicsLayer { rotationZ = rotate1 }
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFF81C784).copy(alpha = if (installState == InstallState.INSTALLING) 0.6f else 0.3f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color(0xFF81C784).copy(alpha = if (installState == InstallState.INSTALLING) 0.6f else 0.3f)
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        // 中环（逆时针）
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .graphicsLayer { rotationZ = rotate2 }
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFF4FC3F7).copy(alpha = 0.4f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color(0xFF4FC3F7).copy(alpha = 0.4f)
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        // 内暗底
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A1A))
                        )
                        // 中心 ZIP 图标 / 状态图标
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer {
                                    scaleX = if (installState == InstallState.INSTALLING) iconBreath else successScale.value
                                    scaleY = if (installState == InstallState.INSTALLING) iconBreath else successScale.value
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (installState == InstallState.SUCCESS) {
                                        Brush.linearGradient(listOf(Color(0xFF81C784), Color(0xFF4CAF50)))
                                    } else if (installState == InstallState.FAILED) {
                                        Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFB71C1C)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF81C784), Color(0xFF4CAF50)))
                                    }
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (installState) {
                                InstallState.INSTALLING -> Text(
                                    "ZIP",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp
                                )
                                InstallState.SUCCESS -> Icon(
                                    Icons.TwoTone.CheckCircle,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                                InstallState.FAILED -> Icon(
                                    Icons.TwoTone.Error,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // 成功光晕扩散
                        if (glowAlpha.value > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFF81C784).copy(alpha = glowAlpha.value),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        fileName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 当前阶段（呼吸光点）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (installState == InstallState.INSTALLING) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = pulseAlpha }
                                    .background(Color(0xFF81C784), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            currentStage,
                            color = Color.White.copy(0.9f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (lastProgress.isNotBlank() && installState == InstallState.INSTALLING) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            lastProgress,
                            color = Color.White.copy(0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (installState != InstallState.INSTALLING) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (installState == InstallState.SUCCESS) {
                                    Color(0xFF81C784).copy(alpha = 0.25f)
                                } else {
                                    Color(0xFFF44336).copy(alpha = 0.25f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (installState == InstallState.SUCCESS) "完成" else "关闭",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 音量键确认面板
        AnimatedVisibility(
            visible = volumePromptMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️ 需要确认", color = Color(0xFFFFC107), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            volumePromptMessage ?: "",
                            color = Color.White.copy(0.85f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    volumePromptMessage = null
                                    scope.launch { HuijuiModuleInstaller.sendVolumeKey(false) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("取消", color = Color.White.copy(0.8f), fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    volumePromptMessage = null
                                    scope.launch { HuijuiModuleInstaller.sendVolumeKey(true) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784).copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("继续", color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
