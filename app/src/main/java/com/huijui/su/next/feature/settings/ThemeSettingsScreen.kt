package com.huijui.su.next.feature.settings

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
import com.huijui.su.next.core.ui.language.ZakoText
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

    // 🚀 7.4 段：删掉了强制改材质的 LaunchedEffect
    //    原逻辑：无壁纸时静默把 materialStyle 改成 NONE，用户手动选的玻璃效果被偷偷抹掉
    //    新逻辑：保留用户选择，在材质选项里给温和提示（见下文材质风格区）

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
        // 🚀 7.4 段：本页遮罩用 wallpaperDimAlpha，滑条实时生效（其他页面在第 8 条统一改）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(AppSettings.wallpaperDimAlpha * 0.7f),
                            Color.Black.copy(AppSettings.wallpaperDimAlpha * 1.4f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White)
                ZakoText("返回", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            ZakoText("主题设置", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            ZakoText("自定义你的纯血管理器外观", color = Color.White.copy(0.85f), fontSize = 14.sp)

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // ================= 壁纸类型 =================
                item {
                    SettingsGroup(title = "壁纸类型") {
                        ThemeOptionRow(
                            "动态视频 (MP4)",
                            "60帧极致渲染，最耗电",
                            AppSettings.wallpaperType == WallpaperType.VIDEO,
                            true
                        ) {
                            AppSettings.wallpaperType = WallpaperType.VIDEO
                            AppSettings.save()
                        }
                        ThemeOptionRow(
                            "静态图片 (JPG/PNG)",
                            "纯净省电，适合日常使用",
                            AppSettings.wallpaperType == WallpaperType.IMAGE,
                            true
                        ) {
                            AppSettings.wallpaperType = WallpaperType.IMAGE
                            AppSettings.save()
                        }
                        ThemeOptionRow(
                            "无壁纸 (纯黑)",
                            "极致性能，回归极客本质",
                            AppSettings.wallpaperType == WallpaperType.NONE,
                            true
                        ) {
                            AppSettings.wallpaperType = WallpaperType.NONE
                            AppSettings.save()
                        }

                        Divider(
                            color = Color.White.copy(0.1f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // 自定义壁纸操作区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { videoPicker.launch("video/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.TwoTone.VideoFile,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                ZakoText("选动态壁纸", fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.TwoTone.Image,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                ZakoText("选静态壁纸", fontSize = 12.sp, color = Color.White)
                            }
                        }

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
                                Icon(
                                    Icons.TwoTone.Delete,
                                    null,
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                ZakoText("清除当前自定义壁纸", fontSize = 14.sp, color = Color(0xFFF44336))
                            }
                        }
                    }
                }

                // ================= 壁纸暗化程度 =================
                item {
                    val isDimEnabled = AppSettings.wallpaperType != WallpaperType.NONE
                    SettingsGroup(title = "壁纸暗化程度", enabled = isDimEnabled) {
                        Text(
                            text = if (isDimEnabled) "壁纸亮度: ${((1f - AppSettings.wallpaperDimAlpha) * 100).toInt()}%"
                            else "无壁纸模式下不可用",
                            color = Color.White.copy(0.85f),
                            fontSize = 12.sp
                        )
                        Slider(
                            value = AppSettings.wallpaperDimAlpha,
                            onValueChange = { AppSettings.wallpaperDimAlpha = it },
                            onValueChangeFinished = { AppSettings.save() },
                            valueRange = 0.0f..0.9f,
                            enabled = isDimEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = if (isDimEnabled) AppSettings.accentColor.color else Color.Gray,
                                activeTrackColor = if (isDimEnabled) AppSettings.accentColor.color else Color.Gray
                            )
                        )
                        // 🚀 7.4 段：小字说明暗化只在本页实时生效，其他页第 8 条统一改
                        Text(
                            text = "提示：暗化效果当前仅在本页实时预览，其他页面将在后续版本统一同步",
                            color = Color.White.copy(0.4f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                // ================= 材质风格 =================
                item {
                    SettingsGroup(title = "材质风格") {
                        // 🚀 7.4 段：去掉 "Haze" 字样（我们根本没用 Haze）
                        //    无壁纸时不再静默强制改材质，改成提示，让用户自己决定
                        ThemeOptionRow(
                            "毛玻璃",
                            if (!isHighAndroidVersion) "您的系统低于 Android 12，不支持此特效"
                            else "原生 Brush 实现，通透质感",
                            AppSettings.materialStyle == MaterialStyle.GLASS,
                            isHighAndroidVersion
                        ) {
                            if (AppSettings.wallpaperType == WallpaperType.NONE) {
                                Toast.makeText(context, "无壁纸模式下玻璃效果无意义，但你的选择会被保留", Toast.LENGTH_LONG).show()
                            }
                            AppSettings.materialStyle = MaterialStyle.GLASS
                            AppSettings.save()
                        }
                        ThemeOptionRow(
                            "纯色半透明",
                            if (AppSettings.wallpaperType == WallpaperType.NONE) "无壁纸时不可用"
                            else "纯原生渐变，性能极高",
                            AppSettings.materialStyle == MaterialStyle.SOLID,
                            AppSettings.wallpaperType != WallpaperType.NONE
                        ) {
                            if (AppSettings.wallpaperType == WallpaperType.NONE) {
                                Toast.makeText(context, "请先设置壁纸！", Toast.LENGTH_SHORT).show()
                            } else {
                                AppSettings.materialStyle = MaterialStyle.SOLID
                                AppSettings.save()
                            }
                        }
                        ThemeOptionRow(
                            "极简无材质",
                            "完全没有背景，全靠内容",
                            AppSettings.materialStyle == MaterialStyle.NONE,
                            true
                        ) {
                            AppSettings.materialStyle = MaterialStyle.NONE
                            AppSettings.save()
                        }
                    }
                }

                // ================= 卡片圆角 =================
                item {
                    SettingsGroup(title = "卡片圆角") {
                        ZakoText(
                            "当前: ${AppSettings.cardCornerRadius.toInt()} dp",
                            color = Color.White.copy(0.85f),
                            fontSize = 12.sp
                        )
                        Slider(
                            value = AppSettings.cardCornerRadius,
                            onValueChange = { AppSettings.cardCornerRadius = it },
                            onValueChangeFinished = { AppSettings.save() },
                            valueRange = 0f..40f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppSettings.accentColor.color,
                                activeTrackColor = AppSettings.accentColor.color
                            )
                        )
                    }
                }

                // ================= 强调色 =================
                item {
                    SettingsGroup(title = "强调色") {
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            AccentColor.values().forEach { accent ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(accent.color)
                                        .border(
                                            if (AppSettings.accentColor == accent) 3.dp else 0.dp,
                                            Color.White,
                                            CircleShape
                                        )
                                        .clickable {
                                            AppSettings.accentColor = accent
                                            AppSettings.save()
                                        }
                                )
                            }
                        }
                    }
                }

                // ================= 过渡动画 =================
                item {
                    SettingsGroup(title = "过渡动画") {
                        ThemeOptionRow(
                            "弹簧物理回弹 (Spring)",
                            "Q弹有力，最推荐",
                            AppSettings.animationStyle == AnimationStyle.SPRING,
                            true
                        ) {
                            AppSettings.animationStyle = AnimationStyle.SPRING
                            AppSettings.save()
                        }
                        ThemeOptionRow(
                            "平滑过渡 (Smooth)",
                            "线性过渡，干练不拖沓",
                            AppSettings.animationStyle == AnimationStyle.SMOOTH,
                            true
                        ) {
                            AppSettings.animationStyle = AnimationStyle.SMOOTH
                            AppSettings.save()
                        }
                        ThemeOptionRow(
                            "无动画",
                            "瞬间切换，极度省电",
                            AppSettings.animationStyle == AnimationStyle.NONE,
                            true
                        ) {
                            AppSettings.animationStyle = AnimationStyle.NONE
                            AppSettings.save()
                        }
                    }
                }

                // ================= 全局动画速度 =================
                item {
                    SettingsGroup(title = "全局动画速度") {
                        ZakoText(
                            "当前: ${AppSettings.animationSpeedScale}x",
                            color = Color.White.copy(0.85f),
                            fontSize = 12.sp
                        )
                        Slider(
                            value = AppSettings.animationSpeedScale,
                            onValueChange = { AppSettings.animationSpeedScale = it },
                            onValueChangeFinished = { AppSettings.save() },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AppSettings.accentColor.color,
                                activeTrackColor = AppSettings.accentColor.color
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    desc: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.TwoTone.RadioButtonChecked else Icons.TwoTone.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.75f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, color = Color.White.copy(0.75f), fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = if (enabled) Color.White.copy(0.85f) else Color.White.copy(0.3f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        // 🚀 7.4 段确认：GlassCard 内部已有 padding(20dp)，这里不再叠加 padding
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

// 🚀 核心底层：将 Uri 拷贝到 App 私有目录，防止重启后失效
suspend fun copyUriToInternal(context: Context, uri: Uri, fileName: String): String? =
    withContext(Dispatchers.IO) {
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
