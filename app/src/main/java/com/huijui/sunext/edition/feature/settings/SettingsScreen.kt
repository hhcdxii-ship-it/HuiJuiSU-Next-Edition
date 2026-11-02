package com.huijui.sunext.edition.feature.settings

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.BuildConfig
import com.huijui.sunext.edition.R
import com.huijui.sunext.edition.core.root.EnvironmentPurge
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.design.HuiIconBadge
import com.huijui.sunext.edition.core.ui.design.HuiSpace
import com.huijui.sunext.edition.core.ui.design.huiSecondary
import com.huijui.sunext.edition.core.ui.design.huiTertiary
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppResetManager
import com.huijui.sunext.edition.core.utils.SystemGuardian
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.launch

/**
 * 设置中心。
 *
 * 视觉上对齐新的设计系统（同 Home / 超管 / 内核模块）：
 * 标题降到 26sp Light + 等宽副标题，行首图标改成圆角图标砖，
 * 次级文字统一走 [huiSecondary] / [huiTertiary]，不再各写一个 alpha。
 *
 * 另外把底部内边距从 120dp 改成 20dp —— 底部导航栏的让位已经在 MainActivity
 * 统一预留过了，这里再加一次就是一大片空白。
 */
@Composable
fun SettingsScreen(
    onNavigateToTheme: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onResetComplete: () -> Unit = {},
    onNavigateToKernelModules: () -> Unit = {},
    onNavigateToEffects: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }
    // 崩溃日志：CrashHandler 一直在往 filesDir 写，但从来没有入口能看到
    var crashLog by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    val accent = AppSettings.accentColor.color

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = HuiSpace.screen),
        contentPadding = PaddingValues(top = HuiSpace.contentTop, bottom = HuiSpace.contentBottom),
        verticalArrangement = Arrangement.spacedBy(HuiSpace.s)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = HuiSpace.s)) {
                ZakoText("设置", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                ZakoText(
                    "HuiJuiSU Next Edition · ${BuildConfig.VERSION_NAME}",
                    color = Color.White.huiTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── 配置与个性化 ────────────────────────────────────────────
        item { SectionLabel("配置与个性化") }
        item {
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "主题设置",
                subtitle = "材质、壁纸、动画与主色",
                onClick = onNavigateToTheme
            )
        }
        item {
            SettingsRow(
                icon = Icons.Default.Language,
                title = "语言设置",
                subtitle = "简繁 / 英日韩俄 / 杂鱼模式",
                onClick = onNavigateToLanguage
            )
        }
        item {
            SettingsRow(
                icon = Icons.Default.Memory,
                title = "内核模块",
                subtitle = "查看内置 8 档模块与当前内核匹配情况",
                onClick = onNavigateToKernelModules
            )
        }
        item {
            SettingsRow(
                icon = Icons.Default.Bolt,
                title = "动效实验室",
                subtitle = "1128 种动效组合 · 实时预览",
                onClick = onNavigateToEffects
            )
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow(
                    icon = Icons.Default.CloudDownload,
                    title = "检查模块更新",
                    subtitle = "进入模块页时按仓库版本比对，标记可更新的模块",
                    checked = AppSettings.isCheckModuleUpdateEnabled,
                    accent = accent
                ) { on ->
                    AppSettings.isCheckModuleUpdateEnabled = on
                    AppSettings.save()
                }
            }
        }

        // ── 引擎特性 ────────────────────────────────────────────────
        item { SectionLabel("HUI 独有特性", modifier = Modifier.padding(top = HuiSpace.m)) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HuiSpace.m)) {
                    FeatureRow(Icons.Default.Hub, "Omni-Root 引擎", "兼容并接管 KSU / Magisk 底层生态")
                    FeatureRow(Icons.Default.Security, "3 级防格机拦截", "白名单分区过滤 + GKI 校验")
                    FeatureRow(Icons.Default.SettingsBackupRestore, "无感热备份", "刷机前自动备份，秒级回滚")
                    FeatureRow(Icons.Default.Language, "原生 WebUI 沙盒", "支持 Magisk / KSU 模块 HTML 控制台")
                }
            }
        }

        // ── 安全与反作弊 ────────────────────────────────────────────
        item { SectionLabel("安全与反作弊", modifier = Modifier.padding(top = HuiSpace.m)) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HuiIconBadge(icon = Icons.Default.Security, tint = accent, size = 38.dp, iconSize = 19.dp)
                        Spacer(Modifier.width(HuiSpace.m))
                        Column(modifier = Modifier.weight(1f)) {
                            ZakoText("作弊哨兵", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            ZakoText(
                                "用 Root 在联机游戏开挂时，自爆 HUI 环境并撤销全部授权",
                                color = Color.White.huiTertiary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = AppSettings.cheatSentinelEnabled,
                            onCheckedChange = {
                                AppSettings.cheatSentinelEnabled = it
                                AppSettings.save()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }
                    if (AppSettings.cheatPurgeCount > 0) {
                        Spacer(Modifier.height(HuiSpace.s))
                        ZakoText(
                            "已触发自爆 ${AppSettings.cheatPurgeCount} 次 · " +
                                EnvironmentPurge.remainingLockText(),
                            color = Color(0xFFFFC107),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ── 系统守护 ────────────────────────────────────────────────
        item { SectionLabel("系统守护", modifier = Modifier.padding(top = HuiSpace.m)) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // 这两个开关此前只写不读、也没有任何 UI ——
                    // SystemGuardian 里写着对应的守护逻辑，但用户永远开不了。
                    ToggleRow(
                        icon = Icons.Default.DeveloperMode,
                        title = "防开发者选项被重置",
                        subtitle = "定期把开发者选项开关改回来（30 秒一次）",
                        checked = AppSettings.isAntiDevOptionsEnabled,
                        accent = accent
                    ) { on ->
                        AppSettings.isAntiDevOptionsEnabled = on
                        AppSettings.save()
                        if (on) SystemGuardian.startGuardian(scope)
                        else if (!AppSettings.isAntiRootLossEnabled) SystemGuardian.stopGuardian()
                    }
                    Spacer(Modifier.height(HuiSpace.m))
                    // 电池优化豁免：系统监控 / Root 审计都是前台服务，
                    // 被厂商的省电策略杀掉之后监控就静默中断了，而用户完全不知道。
                    SettingsRow(
                        icon = Icons.Default.BatteryFull,
                        title = "忽略电池优化",
                        subtitle = "防止系统在后台杀掉监控与守护进程",
                        onClick = {
                            runCatching {
                                val uri = android.net.Uri.parse("package:${context.packageName}")
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        uri
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法打开电池优化设置，请手动在系统设置里关闭", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    Spacer(Modifier.height(HuiSpace.m))
                    ToggleRow(
                        icon = Icons.Default.Shield,
                        title = "Root 掉线守护",
                        subtitle = "检测到 Root 丢失时保持环境不被系统回收",
                        checked = AppSettings.isAntiRootLossEnabled,
                        accent = accent
                    ) { on ->
                        AppSettings.isAntiRootLossEnabled = on
                        AppSettings.save()
                        if (on) SystemGuardian.startGuardian(scope)
                        else if (!AppSettings.isAntiDevOptionsEnabled) SystemGuardian.stopGuardian()
                    }
                }
            }
        }

        // ── 危险操作 ────────────────────────────────────────────────
        item { SectionLabel("重置与危险操作", modifier = Modifier.padding(top = HuiSpace.m)) }
        item {
            SettingsRow(
                icon = Icons.Default.RestartAlt,
                title = "恢复出厂设置",
                subtitle = "清空主题、壁纸、引导状态（保留 Root 授权）",
                danger = true,
                onClick = { showResetDialog = true }
            )
        }

        // ── 诊断 ────────────────────────────────────────────────────
        item { SectionLabel("诊断", modifier = Modifier.padding(top = HuiSpace.m)) }
        item {
            SettingsRow(
                icon = Icons.Default.Share,
                title = "导出诊断报告",
                subtitle = "汇总版本 / Root 状态 / 模块 / 崩溃日志，用系统分享发出去",
                onClick = {
                    scope.launch {
                        val report = runCatching {
                            com.huijui.sunext.edition.core.utils.DiagnosticReporter.build(context)
                        }.getOrElse { "诊断报告生成失败：${it.message}" }
                        runCatching {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "HuiJuiSU 诊断报告")
                                putExtra(android.content.Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(send, "分享诊断报告")
                            )
                        }.onFailure {
                            Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                icon = Icons.Default.BugReport,
                title = "崩溃日志",
                subtitle = "查看最近一次崩溃的堆栈",
                onClick = {
                    crashLog = runCatching {
                        val f = java.io.File(context.filesDir, "hui_crash.log")
                        if (f.exists()) f.readText().takeLast(8000) else ""
                    }.getOrDefault("")
                }
            )
        }

        // ── 关于 ────────────────────────────────────────────────────
        item { SectionLabel("关于", modifier = Modifier.padding(top = HuiSpace.m)) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onNavigateToAbout) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_brand),
                        contentDescription = "Logo",
                        modifier = Modifier.size(52.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(HuiSpace.m))
                    Column(modifier = Modifier.weight(1f)) {
                        ZakoText("HuiJuiSU NEXT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        ZakoText(
                            "v${BuildConfig.VERSION_NAME} · 纯血 LKM",
                            color = Color.White.huiTertiary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        ZakoText("Next", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAbout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_brand),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(HuiSpace.m))
                    Column {
                        ZakoText("HuiJuiSU NEXT", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        ZakoText(
                            "v${BuildConfig.VERSION_NAME} · 纯血 LKM",
                            color = Color.White.huiTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutLink(
                        emoji = "🐙",
                        title = "GitHub 源码",
                        subtitle = "github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition"
                    ) {
                        openUrl(context, "https://github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition")
                    }
                    AboutLink(
                        emoji = "💚",
                        title = "酷安主页",
                        subtitle = "coolapk.com/u/43549687"
                    ) {
                        openUrl(context, "https://www.coolapk.com/u/43549687")
                    }
                    AboutLink(
                        emoji = "📦",
                        title = "内核模块下载",
                        subtitle = "GitHub Releases · 8 个 KMI 的 .ko"
                    ) {
                        openUrl(context, "https://github.com/hhcdxii-ship-it/HuiJuiSU-Next-Edition/releases")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    ZakoText("关闭", color = Color.White.huiSecondary)
                }
            },
            containerColor = Color(0xFF1C1C21)
        )
    }

    crashLog?.let { log ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { ZakoText("崩溃日志", color = Color.White) },
            text = {
                if (log.isBlank()) {
                    ZakoText("还没有崩溃记录，挺好。", color = Color.White.huiSecondary)
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.heightIn(max = 380.dp)
                    ) {
                        androidx.compose.foundation.rememberScrollState().let { scroll ->
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.verticalScroll(scroll)
                            ) {
                                ZakoText(
                                    log,
                                    color = Color.White.huiSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        java.io.File(context.filesDir, "hui_crash.log").delete()
                    }
                    crashLog = null
                }) {
                    ZakoText("清除记录", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { crashLog = null }) {
                    ZakoText("关闭", color = Color.White.huiSecondary)
                }
            },
            containerColor = Color(0xFF1C1C21)
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { ZakoText("恢复出厂设置", color = Color.White) },
            text = {
                ZakoText(
                    "将清空所有主题、壁纸、引导状态，保留 Root 授权记录。此操作不可撤销，是否继续？",
                    color = Color.White.huiSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    isResetting = true
                    scope.launch {
                        val result = AppResetManager.resetToFactory(context)
                        isResetting = false
                        showResetDialog = false
                        if (result.success) onResetComplete()
                        else Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    ZakoText("确认重置", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    ZakoText("取消", color = Color.White.huiSecondary)
                }
            },
            containerColor = Color(0xFF1C1C21)
        )
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HuiIconBadge(icon = icon, tint = accent, size = 34.dp, iconSize = 17.dp, corner = 10.dp)
        Spacer(Modifier.width(HuiSpace.m))
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            ZakoText(subtitle, color = Color.White.huiTertiary, fontSize = 10.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
private fun AboutLink(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZakoText(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ZakoText(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, maxLines = 1)
        }
        ZakoText("›", color = Color.White.copy(alpha = 0.35f), fontSize = 16.sp)
    }
}

/** 打开外部链接；没有浏览器时给个提示而不是崩。 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        android.widget.Toast.makeText(context, "没有可用的浏览器", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    ZakoText(
        text,
        color = Color.White.huiTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) Color(0xFFEF4444) else AppSettings.accentColor.color

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HuiIconBadge(icon = icon, tint = tint, size = 38.dp, iconSize = 19.dp)
            Spacer(modifier = Modifier.width(HuiSpace.m))
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(
                    title,
                    color = if (danger) Color(0xFFEF4444) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                ZakoText(subtitle, color = Color.White.huiTertiary, fontSize = 11.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HuiIconBadge(icon = icon, tint = AppSettings.accentColor.color, size = 32.dp, iconSize = 16.dp, corner = 10.dp)
        Spacer(modifier = Modifier.width(HuiSpace.m))
        Column {
            ZakoText(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(1.dp))
            ZakoText(subtitle, color = Color.White.huiTertiary, fontSize = 10.5.sp)
        }
    }
}
