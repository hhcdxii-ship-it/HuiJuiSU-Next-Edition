package com.huijui.sunext.edition.feature.toolbox

import android.net.Uri
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.ui.language.ZakoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CoreOverrideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMode by remember { mutableStateOf("live") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var isPatched by remember { mutableStateOf(false) }
    var patchedImgPath by remember { mutableStateOf<String?>(null) }
    var processLog by remember { mutableStateOf("") }

    // 🚀 核心：动态检测是否有 Root 权限
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            hasRoot = HuijuiShell.isRooted()
        }
        // 如果没有 Root，强制把选中模式切到本地镜像
        if (hasRoot == false) {
            selectedMode = "rebuild"
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedUri = it
            selectedFileName = it.lastPathSegment?.substringAfterLast('/') ?: "unknown.img"
            selectedMode = "rebuild"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                ZakoText("返回", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            ZakoText("底层重载协议", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Thin, fontFamily = FontFamily.Monospace)
            ZakoText("CORE OVERRIDE // 重启后生效", color = Color.White.copy(0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(24.dp))

            if (hasRoot == null) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    
                    // 🚀 只有拥有 Root 才显示这两个选项！
                    if (hasRoot == true) {
                        item {
                            OverrideModeCard(
                                title = "直接安装 (推荐)", desc = "修补当前运行的活动分区（需 Root 权限）", icon = Icons.Default.FlashOn, accent = Color(0xFF00E5FF),
                                isSelected = selectedMode == "live", onClick = { selectedMode = "live" }
                            )
                        }
                        item {
                            OverrideModeCard(
                                title = "安装到未使用槽位 (OTA后)", desc = "为下一次系统 OTA 准备，修补空闲槽位以保留 Root", icon = Icons.Default.Security, accent = Color(0xFF8B5CF6),
                                isSelected = selectedMode == "ghost", onClick = { selectedMode = "ghost" }
                            )
                        }
                    }

                    // 🚀 没 Root 的可怜虫提示
                    if (hasRoot == false) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF2A85).copy(alpha = 0.1f)).border(1.dp, Color(0xFFFF2A85).copy(0.5f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                                ZakoText("连 Root 都没有，只能选择本地镜像", color = Color(0xFFFF2A85), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 这个永远显示
                    item {
                        OverrideModeCard(
                            title = "选择本地镜像修补", desc = if (selectedUri == null) "点击选择本地的 boot.img 或 init_boot.img" else "已选择: $selectedFileName", icon = Icons.Default.Memory, accent = Color(0xFFFF2A85),
                            isSelected = selectedMode == "rebuild",
                            onClick = { if (selectedUri == null) filePicker.launch("*/*") else selectedMode = "rebuild" }
                        )
                    }

                    if (processLog.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                                ZakoText("> 控制台输出", color = Color(0xFF00E5FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(8.dp))
                                ZakoText(processLog, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                            }
                        }
                    }

                    item {
                        val activeColor = when(selectedMode) { "live" -> Color(0xFF00E5FF); "ghost" -> Color(0xFF8B5CF6); else -> Color(0xFFFF2A85) }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(24.dp))
                                .background(activeColor.copy(alpha = 0.15f)).border(1.dp, activeColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .clickable(enabled = !isProcessing) {
                                    if (hasRoot == false) {
                                        // 🚀 没 Root，直接弹系统授权提示
                                        Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("请授予 Root 权限"), Toast.LENGTH_SHORT).show()
                                        scope.launch { HuijuiShell.exec("su -c id") }
                                        return@clickable
                                    }
                                    
                                    if (isPatched) {
                                        // 🚀 终极闭环：点击直接下发重启指令
                                        scope.launch {
                                            processLog += "> 正在下发重启指令...\n"
                                            HuijuiShell.exec("su -c reboot")
                                        }
                                    } else if (!isProcessing) {
                                        if (patchedImgPath != null) {
                                        // 🚀 如果是本地镜像修补完成，点击直接复制路径
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Path", patchedImgPath))
                                        android.widget.Toast.makeText(context, "路径已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (isPatched) {
                                        // 🚀 直接刷入模式，点击重启
                                        scope.launch {
                                            processLog += "> 正在下发重启指令...\n"
                                            HuijuiShell.exec("su -c reboot")
                                        }
                                    } else if (!isProcessing) {
                                        isProcessing = true
                                        processLog = "> 正在请求临时 Root 权限...\n"
                                        patchedImgPath = null
                                        isPatched = false
                                        scope.launch {
                                            val success = HuijuiInstaller.installLkm(context, selectedMode == "ghost", selectedUri) { log ->
                                                processLog += "${log}\n"
                                            }
                                            if (success) {
                                                if (selectedUri != null) {
                                                    // 本地修补成功
                                                    patchedImgPath = "/sdcard/Download/HuiJuiPhantom_patched.img"
                                                } else {
                                                    // 直接刷入成功
                                                    isPatched = true
                                                }
                                            }
                                            isProcessing = false
                                        }
                                    }
                                        }
                                    }
                                    }
                                }, contentAlignment = Alignment.Center
                        ) {
                            if (isProcessing) CircularProgressIndicator(color = activeColor, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            else ZakoText(
                                if (patchedImgPath != null) "复制镜像路径" 
                                else if (isPatched) "立即重启设备" 
                                else if (hasRoot == false) "请求权限" 
                                else "执行重载", 
                                color = if(patchedImgPath != null || isPatched) Color.Black else activeColor, 
                                fontSize = 16.sp, 
                                fontWeight = FontWeight.Bold, 
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverrideModeCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, isSelected: Boolean, onClick: () -> Unit) {
    val bgBrush = if (isSelected) Brush.verticalGradient(listOf(accent.copy(alpha = 0.2f), Color(0x1FFFFFFF))) else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1FFFFFFF)))
    val borderColor = if (isSelected) accent.copy(alpha = 0.8f) else Color(0x2BFFFFFF)
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bgBrush).border(1.dp, borderColor, RoundedCornerShape(24.dp)).clickable { onClick() }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
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
