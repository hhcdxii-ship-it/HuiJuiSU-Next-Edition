package com.huijui.sunext.edition.feature.toolbox

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HsudInstaller
import com.huijui.sunext.edition.core.root.AdbUsbController
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.language.zakoString
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.launch

@Composable
fun CoreOverrideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeColor = AppSettings.accentColor.color

    var selectedMode by remember { mutableStateOf("live") } // live, ota, local
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    
    var isProcessing by remember { mutableStateOf(false) }
    var isPatched by remember { mutableStateOf(false) }
    var patchedImgPath by remember { mutableStateOf<String?>(null) }
    var processLog by remember { mutableStateOf("") }

    // 本地镜像选择器
    val imgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            selectedFileName = "已选择本地镜像"
            selectedMode = "local"
            processLog += "> 已加载本地镜像: ${it.lastPathSegment}\n"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        ZakoText("底层重载协议", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        ZakoText("CORE OVERRIDE // 重启后生效", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                OverrideModeCard(
                    title = "直接安装 (推荐)",
                    desc = "修补当前运行的活动分区（需 Root 权限）",
                    icon = Icons.Default.FlashOn,
                    accent = Color(0xFF00E5FF),
                    isSelected = selectedMode == "live",
                    onClick = { selectedMode = "live"; selectedUri = null }
                )
            }
            
            item {
                OverrideModeCard(
                    title = "安装到未使用槽位 (OTA后)",
                    desc = "为下一次系统 OTA 准备，修补空闲槽位",
                    icon = Icons.Default.Security,
                    accent = Color(0xFF8B5CF6),
                    isSelected = selectedMode == "ghost",
                    onClick = { selectedMode = "ghost"; selectedUri = null }
                )
            }

            item {
                OverrideModeCard(
                    title = "选择本地镜像修补",
                    desc = selectedFileName ?: "点击选择本地的 boot.img 或 init_boot.img",
                    icon = Icons.Default.Memory,
                    accent = Color(0xFFFF2A85),
                    isSelected = selectedMode == "local",
                    onClick = { imgPickerLauncher.launch("application/octet-stream") }
                )
            }

            item {
                var adbLog by remember { mutableStateOf("") }
                var isAdbProcessing by remember { mutableStateOf(false) }
                OverrideModeCard(
                    title = "强制开启 ADB 调试",
                    desc = if (adbLog.isEmpty()) "一键注入调试通道，彻底告别电脑连接弹窗" else adbLog,
                    icon = Icons.Default.Usb,
                    accent = Color(0xFF4CAF50),
                    isSelected = false,
                    onClick = {
                        if (!isAdbProcessing) {
                            isAdbProcessing = true
                            adbLog = "正在执行..."
                            scope.launch {
                                val log = AdbUsbController.forceEnableAdb(context)
                                adbLog = log
                                isAdbProcessing = false
                            }
                        }
                    }
                )
            }


            if (processLog.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        ZakoText("> 控制台输出", color = activeColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))
                        ZakoText(processLog, color = Color.White.copy(0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // 底部悬浮按钮
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp)
        ) {
            val bgBrush = if (true) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
                          else Brush.linearGradient(listOf(activeColor, activeColor.copy(0.8f)))
            
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgBrush)
                    .clickable(enabled = !isProcessing) {
                        if (patchedImgPath != null) {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Path", patchedImgPath))
                            Toast.makeText(context, "路径已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        } else if (isPatched) {
                            scope.launch { HuijuiShell.exec("su -c reboot") }
                        } else if (!isProcessing) {
                            isProcessing = true
                            processLog = "> 正在唤醒 hsud 守护进程...\n"
                            scope.launch {
                                val success = HsudInstaller.installLkm(
                                    context = context,
                                    installToInactiveSlot = selectedMode == "ghost",
                                    localImgUri = selectedUri
                                ) { log -> processLog += "$log\n" }
                                
                                if (success) {
                                    if (selectedUri != null) patchedImgPath = "/sdcard/Download/HuiJuiPhantom_patched.img"
                                    else isPatched = true
                                }
                                isProcessing = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    ZakoText(
                        if (patchedImgPath != null) "复制镜像路径"
                        else if (isPatched) "立即重启设备"
                        else "执行重载",
                        color = if (patchedImgPath != null || isPatched) Color.Black else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OverrideModeCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, isSelected: Boolean, onClick: () -> Unit) {
    val bgBrush = if (true) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
    val borderColor = if (isSelected) accent.copy(alpha = 0.8f) else Color(0x2BFFFFFF)
    
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.12f)).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent))).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)).clickable { onClick() }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).background(if (isSelected) accent.copy(0.2f) else Color.White.copy(0.05f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (isSelected) accent else Color.White.copy(0.6f), modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = if (isSelected) Color.White else Color.White.copy(0.7f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            ZakoText(desc, color = if (isSelected) Color.White.copy(0.7f) else Color.White.copy(0.5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
    }
}
