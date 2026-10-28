package com.huijui.su.next.feature.settings
import com.huijui.su.next.core.ui.language.ZakoText
import androidx.compose.material3.HorizontalDivider

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.SmartBackground
import com.huijui.su.next.core.utils.AppSettings
import com.huijui.su.next.core.utils.SystemGuardian
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onNavigateToTheme: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500), label = "enter_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(0.8f)))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))
            ZakoText("设置", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            // 🚀 动态显示 ROM 标识
            ZakoText("Next Edition · ${AppSettings.osMajorVersion} 适配", color = Color.White.copy(0.85f), fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize().scale(enterAlpha)
            ) {
                // 1. 配置
                item {
                    SettingsGroup(title = "配置") {
                        SettingSwitchRow(Icons.TwoTone.Refresh, "检查管理器更新", "自动检查并提示管理器新版本", AppSettings.isCheckManagerUpdateEnabled) { AppSettings.isCheckManagerUpdateEnabled = it; AppSettings.save() }
                        SettingSwitchRow(Icons.TwoTone.Archive, "检查模块更新", "自动检查已安装模块的更新", AppSettings.isCheckModuleUpdateEnabled) { AppSettings.isCheckModuleUpdateEnabled = it; AppSettings.save() }
                    }
                }

                // 2. 个性化
                item {
                    SettingsGroup(title = "个性化") {
                        SettingActionRow(Icons.TwoTone.ColorLens, "主题设置", "自定义材质、壁纸与动画效果", onNavigateToTheme)
                    }
                }

                // 3. 安全与隐私 (全局)
                item {
                    SettingsGroup(title = "安全与隐私") {
                        SettingSwitchRow(Icons.TwoTone.Security, "隐藏 SELinux 修改", "阻止应用检测到系统的 SELinux 状态变更", AppSettings.isHideSelinuxEnabled) { AppSettings.isHideSelinuxEnabled = it; AppSettings.save() }
                        SettingSwitchRow(Icons.TwoTone.VisibilityOff, "隐藏 Root 痕迹", "尽可能抹除对特定目录的挂载痕迹", AppSettings.isHideRootEnabled) { AppSettings.isHideRootEnabled = it; AppSettings.save() }
                        SettingSwitchRow(Icons.TwoTone.Shield, "防止 Root 丢失", "后台守护，尝试保持 Root 环境在线", AppSettings.isAntiRootLossEnabled) { 
                            AppSettings.isAntiRootLossEnabled = it; AppSettings.save()
                            if (it) SystemGuardian.startGuardian(scope) else SystemGuardian.stopGuardian()
                        }
                    }
                }

                // 4. 小米/HyperOS 专属守护
                if (AppSettings.isXiaomiDevice) {
                    item {
                        SettingsGroup(title = "${AppSettings.osMajorVersion} 系统专属守护") {
                            SettingSwitchRow(Icons.TwoTone.DeveloperMode, "防开发者选项关闭", "强制系统保持开发者选项与 ADB 调试开启", AppSettings.isAntiDevOptionsEnabled) { 
                                AppSettings.isAntiDevOptionsEnabled = it; AppSettings.save()
                                if (it) SystemGuardian.startGuardian(scope)
                            }
                            SettingSwitchRow(Icons.TwoTone.Wallpaper, "防主题壁纸被恢复", "防止系统在重启或清理后重置自定义壁纸", AppSettings.isAntiWallpaperResetEnabled) { 
                                AppSettings.isAntiWallpaperResetEnabled = it; AppSettings.save()
                                Toast.makeText(context, if (it) "已开启系统壁纸守护" else "已关闭守护", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 5. 高级维护
                item {
                    SettingsGroup(title = "高级维护") {
                        SettingActionRow(Icons.TwoTone.CleaningServices, "清理应用缓存", "释放管理器自身的临时文件") { 
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "管理器缓存已清理", Toast.LENGTH_SHORT).show()
                        }
                        SettingActionRow(Icons.TwoTone.RestartAlt, "重启管理器", "强制重启当前 App (刷新底层状态)") { 
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            context.startActivity(intent)
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    }
                }

                // 6. 支持与反馈
                item {
                    SettingsGroup(title = "支持与反馈") {
                        SettingActionRow(Icons.TwoTone.Code, "GitHub 仓库", "查看源码、提交 Issue 或点个 Star") {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition"))
                            context.startActivity(intent)
                        }
                        SettingActionRow(Icons.TwoTone.Send, "发送日志", "导出当前运行日志供开发者排查") { 
                            scope.launch {
                                HuijuiShell.exec("logcat -d > /sdcard/Download/HuiJuiSU_Log.txt")
                                Toast.makeText(context, "日志已导出至 /sdcard/Download/", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                // 7. 关于
                item {
                    SettingsGroup(title = "关于") {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ZakoText("HuiJuiSU", fontSize = 28.sp, fontWeight = FontWeight.Black, style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(AppSettings.accentColor.color, Color(0xFF4CAF50)))))
                            Spacer(modifier = Modifier.height(8.dp))
                            ZakoText("Next Edition · 纯血架构", color = Color.White.copy(0.75f), fontSize = 12.sp, letterSpacing = 2.sp)
                        }
                        HorizontalDivider(color = Color.White.copy(0.1f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                        SettingActionRow(Icons.TwoTone.Info, "版本信息", "v1.0.0-release (Build 1)") { }
                        SettingActionRow(Icons.TwoTone.Face, "开发者", "HuiJuiSU Team (酷安 @喜羊羊灰灰菜單)") { }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Color.White.copy(0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() } }
    }
}

@Composable
fun SettingSwitchRow(icon: ImageVector, title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppSettings.accentColor.color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, color = Color.White.copy(0.75f), fontSize = 12.sp)
        }
        LiquidSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingActionRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(0.8f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, color = Color.White.copy(0.75f), fontSize = 12.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(0.75f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 20.dp else 0.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "thumb_offset")
    val bgColor by animateColorAsState(targetValue = if (checked) AppSettings.accentColor.color else Color.White.copy(0.2f), animationSpec = tween(300), label = "bg_color")
    Box(modifier = Modifier.width(44.dp).height(24.dp).clip(CircleShape).background(bgColor).padding(2.dp), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.offset(x = thumbOffset).size(20.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}
