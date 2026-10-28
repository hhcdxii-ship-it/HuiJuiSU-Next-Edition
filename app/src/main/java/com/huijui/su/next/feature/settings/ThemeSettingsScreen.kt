package com.huijui.su.next.feature.settings
import com.huijui.su.next.core.utils.AppSettings

import android.content.Context
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.SmartBackground
import com.huijui.su.next.core.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighAndroidVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 🚀 选择视频/图片的回调
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val path = copyUriToInternal(context, it, "custom_bg.mp4")
                if (path != null) {
                    AppSettings.customVideoPath = path
                    AppSettings.customImagePath = null
                    AppSettings.wallpaperType = WallpaperType.VIDEO
                    AppSettings.save()
                    Toast.makeText(context, "动态壁纸已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "文件复制失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val path = copyUriToInternal(context, it, "custom_bg.jpg")
                if (path != null) {
                    AppSettings.customImagePath = path
                    AppSettings.customVideoPath = null
                    AppSettings.wallpaperType = WallpaperType.IMAGE
                    AppSettings.save()
                    Toast.makeText(context, "静态壁纸已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "文件复制失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(0.8f)))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White)
                Text("返回", color = Color.White, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("主题设置", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("自定义你的纯血管理器外观", color = Color.White.copy(0.85f), fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
                // ================= 壁纸类型 =================
                item {
                    SettingsGroup(title = "壁纸类型") {
                        ThemeOptionRow("动态视频 (MP4)", "60帧极致渲染，最耗电", AppSettings.wallpaperType == WallpaperType.VIDEO, true) { AppSettings.wallpaperType = WallpaperType.VIDEO; AppSettings.save() }
                        ThemeOptionRow("静态图片 (JPG/PNG)", "纯净省电，适合日常使用", AppSettings.wallpaperType == WallpaperType.IMAGE, true) { AppSettings.wallpaperType = WallpaperType.IMAGE; AppSettings.save() }
                        ThemeOptionRow("无壁纸 (纯黑)", "极致性能，回归极客本质", AppSettings.wallpaperType == WallpaperType.NONE, true) { AppSettings.wallpaperType = WallpaperType.NONE; AppSettings.save() }
                        
                        Divider(color = Color.White.copy(0.1f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                        
                        // 🚀 自定义壁纸操作区
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { videoPicker.launch("video/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.TwoTone.VideoFile, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("选动态壁纸", fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.TwoTone.Image, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("选静态壁纸", fontSize = 12.sp, color = Color.White)
                            }
                        }
                        
                        // 清除壁纸按钮
                        if (AppSettings.customVideoPath != null || AppSettings.customImagePath != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { 
                                    AppSettings.clearCustomWallpaper(context)
                                    Toast.makeText(context, "壁纸已清除", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336).copy(0.2f)),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.TwoTone.Delete, null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("清除当前自定义壁纸", fontSize = 14.sp, color = Color(0xFFF44336))
                            }
                        }
                    }
                }

                // ================= 壁纸暗化程度 =================
                item {
                    val isDimEnabled = AppSettings.wallpaperType != WallpaperType.NONE
                    SettingsGroup(title = "壁纸暗化程度", enabled = isDimEnabled) {
                        Text(
                            text = if (isDimEnabled) "当前: ${(AppSettings.wallpaperDimAlpha * 100).toInt()}%" else "无壁纸模式下不可用",
                            color = Color.White.copy(0.85f), fontSize = 12.sp
                        )
                        Slider(
                            value = AppSettings.wallpaperDimAlpha,
                            onValueChange = { AppSettings.wallpaperDimAlpha = it },
                            onValueChangeFinished = { AppSettings.save() },
                            valueRange = 0.0f..0.9f,
                            enabled = isDimEnabled,
                            colors = SliderDefaults.colors(thumbColor = if (isDimEnabled) AppSettings.accentColor.color else Color.Gray, activeTrackColor = if (isDimEnabled) AppSettings.accentColor.color else Color.Gray)
                        )
                    }
                }

                // ================= 材质风格 =================
                item {
                    SettingsGroup(title = "材质风格") {
                        ThemeOptionRow("液态玻璃 (Haze)", if (isHighAndroidVersion) "真实背景折射，质感最佳" else "您的系统低于 Android 12，不支持此特效", AppSettings.materialStyle == MaterialStyle.GLASS, isHighAndroidVersion) { AppSettings.materialStyle = MaterialStyle.GLASS; AppSettings.save() }
                        ThemeOptionRow("纯色半透明", "纯原生渐变，性能极高", AppSettings.materialStyle == MaterialStyle.SOLID, true) { AppSettings.materialStyle = MaterialStyle.SOLID; AppSettings.save() }
                        ThemeOptionRow("极简无材质", "完全没有背景，全靠内容", AppSettings.materialStyle == MaterialStyle.NONE, true) { AppSettings.materialStyle = MaterialStyle.NONE; AppSettings.save() }
                    }
                }

                // ================= 卡片圆角 =================
                item {
                    SettingsGroup(title = "卡片圆角") {
                        Text("当前: ${AppSettings.cardCornerRadius.toInt()} dp", color = Color.White.copy(0.85f), fontSize = 12.sp)
                        Slider(
                            value = AppSettings.cardCornerRadius,
                            onValueChange = { AppSettings.cardCornerRadius = it },
                            onValueChangeFinished = { AppSettings.save() },
                            valueRange = 0f..40f,
                            colors = SliderDefaults.colors(thumbColor = AppSettings.accentColor.color, activeTrackColor = AppSettings.accentColor.color)
                        )
                    }
                }

                // ================= 强调色 =================
                item {
                    SettingsGroup(title = "强调色") {
                        Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            AccentColor.values().forEach { accent ->
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.color)
                                        .border(if (AppSettings.accentColor == accent) 3.dp else 0.dp, Color.White, CircleShape)
                                        .clickable { AppSettings.accentColor = accent; AppSettings.save() }
                                )
                            }
                        }
                    }
                }

                // ================= 过渡动画 =================
                item {
                    SettingsGroup(title = "过渡动画") {
                        ThemeOptionRow("弹簧物理回弹 (Spring)", "Q弹有力，最推荐", AppSettings.animationStyle == AnimationStyle.SPRING, true) { AppSettings.animationStyle = AnimationStyle.SPRING; AppSettings.save() }
                        ThemeOptionRow("平滑过渡 (Smooth)", "线性过渡，干练不拖沓", AppSettings.animationStyle == AnimationStyle.SMOOTH, true) { AppSettings.animationStyle = AnimationStyle.SMOOTH; AppSettings.save() }
                        ThemeOptionRow("无动画", "瞬间切换，极度省电", AppSettings.animationStyle == AnimationStyle.NONE, true) { AppSettings.animationStyle = AnimationStyle.NONE; AppSettings.save() }
                    }
                }
                
                // ================= 全局动画速度 =================
                item {
                    SettingsGroup(title = "全局动画速度") {
                        Text("当前: ${AppSettings.animationSpeedScale}x", color = Color.White.copy(0.85f), fontSize = 12.sp)
                        Slider(
                            value = AppSettings.animationSpeedScale,
                            onValueChange = { AppSettings.animationSpeedScale = it },
                            onValueChangeFinished = { AppSettings.save() },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = AppSettings.accentColor.color, activeTrackColor = AppSettings.accentColor.color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionRow(title: String, desc: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() }.alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.TwoTone.RadioButtonChecked else Icons.TwoTone.RadioButtonUnchecked,
            contentDescription = null, tint = if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.75f), modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, color = Color.White.copy(0.75f), fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsGroup(title: String, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = if (enabled) Color.White.copy(0.85f) else Color.White.copy(0.3f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() } }
    }
}

// 🚀 核心底层：将 Uri 拷贝到 App 私有目录，防止重启后失效
suspend fun copyUriToInternal(context: Context, uri: Uri, fileName: String): String? = withContext(Dispatchers.IO) {
    try {
        val destFile = File(context.filesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return@withContext destFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
