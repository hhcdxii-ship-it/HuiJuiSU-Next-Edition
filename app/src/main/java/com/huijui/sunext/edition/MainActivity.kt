package com.huijui.sunext.edition

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import com.huijui.sunext.edition.core.ui.language.zakoString

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.huijui.sunext.edition.core.ui.theme.HuiTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.huijui.sunext.edition.core.root.RootStatusRepository
import com.huijui.sunext.edition.core.ui.component.LocalStrongGlass
import com.huijui.sunext.edition.core.ui.component.SmartBackground
import com.huijui.sunext.edition.core.ui.language.HuiStrings
import com.huijui.sunext.edition.core.ui.language.LanguageMode
import com.huijui.sunext.edition.core.ui.language.LocalLanguageMode
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.ui.theme.HuijuiTheme
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.feature.module.ModuleScreen
import com.huijui.sunext.edition.feature.module.ModuleWebUIScreen
import com.huijui.sunext.edition.feature.onboarding.OnboardingScreen
import com.huijui.sunext.edition.feature.settings.LanguageSettingsScreen
import com.huijui.sunext.edition.feature.settings.SettingsScreen
import com.huijui.sunext.edition.feature.settings.ThemeSettingsScreen
import com.huijui.sunext.edition.feature.superuser.SuperUserScreen
import com.huijui.sunext.edition.feature.toolbox.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NavItem(val route: String, val title: String, val icon: ImageVector)

/** 底部导航栏实际占用的高度（72dp 本体 + 上下 24dp 外距 + 手势条）。 */
private val BOTTOM_BAR_RESERVED = 124.dp

class MainActivity : ComponentActivity() {
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前：SplashScreen 要在窗口创建前接管启动图，
        // 并在首帧后把主题切回 postSplashScreenTheme（否则会一直停在启动主题上）
        installSplashScreen()

        super.onCreate(savedInstanceState)
        com.huijui.sunext.edition.core.utils.HighRefreshRateHelper.enableMaxRefreshRate(this)
        AppSettings.init(this)
        // 崩溃捕获：此前 CrashHandler 全项目零引用，等于一次都没装上，
        // 线上崩了不会有任何本地日志可查。
        com.huijui.sunext.edition.core.utils.CrashHandler.init(this)

        // 通知权限：Manifest 里声明了 POST_NOTIFICATIONS，但全项目**从来没有申请过**。
        // Android 13+ 上没这个权限，系统监控 / Root 审计那两个前台服务的通知不会显示，
        // 用户看不到、也点不到「停止」，某些 OEM 还会因为"无通知的前台服务"直接杀进程。
        requestNotificationPermissionIfNeeded()

        // 跟随系统的「移除动画」：动画缩放为 0 时关掉我们所有动效
        runCatching {
            val scale = android.provider.Settings.Global.getFloat(
                contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            com.huijui.sunext.edition.core.ui.effect.HuiMotion.systemDisablesAnimations = scale == 0f
        }
        setContent {
            HuijuiTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val scope = rememberCoroutineScope()

    // 系统守护：只在用户真的开了对应开关时才跑，关掉就停，
    // 避免一个空转的 10 秒轮询白白耗电。
    DisposableEffect(Unit) {
        val wanted = AppSettings.isAntiDevOptionsEnabled || AppSettings.isAntiRootLossEnabled
        if (wanted) {
            com.huijui.sunext.edition.core.utils.SystemGuardian.startGuardian(scope)
        }
        onDispose { com.huijui.sunext.edition.core.utils.SystemGuardian.stopGuardian() }
    }

    // 生命期感知：切回前台强制重新探测一次（走统一状态源，不再各自起 su）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch(Dispatchers.IO) {
                    RootStatusRepository.refresh(force = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hideBottomBar = currentRoute == "onboarding" ||
        currentRoute?.startsWith("toolbox_") == true ||
        currentRoute == "core_override" ||
        currentRoute == "kernel_modules" ||
        currentRoute == "effects_gallery" ||
        currentRoute == "module_webui" ||
        currentRoute == "language_settings"
    val forceStrongGlass = currentRoute?.startsWith("toolbox_") == true || currentRoute == "module_webui"

    val languageMode = remember(AppSettings.currentLanguage) {
        HuiStrings.modeOf(AppSettings.currentLanguage)
    }

    CompositionLocalProvider(
        LocalLanguageMode provides languageMode,
        LocalStrongGlass provides forceStrongGlass
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentRoute != "onboarding") {
                SmartBackground()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (AppSettings.wallpaperDimAlpha * 1.2f).coerceIn(0f, 1f)))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (currentRoute == "onboarding") Modifier else Modifier.statusBarsPadding())
                    .padding(bottom = if (hideBottomBar) 0.dp else BOTTOM_BAR_RESERVED)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (AppSettings.onboardingCompleted) "home" else "onboarding",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        fadeIn(tween(220)) + scaleIn(initialScale = 0.986f, animationSpec = tween(220))
                    },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(140)) }
                ) {
                    composable("onboarding") {
                        OnboardingScreen(onFinish = {
                            AppSettings.onboardingCompleted = true
                            AppSettings.save()
                            navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        ToolboxScreen(
                            onNavigateToFlash = { navController.navigate("toolbox_flash") },
                            onNavigateToOverride = { navController.navigate("core_override") },
                            onNavigateToModule = { navController.navigate("module") },
                            onNavigateToHotInstall = { navController.navigate("toolbox_console") }
                        )
                    }
                    composable("core_override") { CoreOverrideScreen(onBack = { navController.popBackStack() }) }
                    composable("kernel_modules") { KernelModulesScreen(onBack = { navController.popBackStack() }) }
                    composable("effects_gallery") { EffectsGalleryScreen(onBack = { navController.popBackStack() }) }

                    // ============================================================
                    // ⚠️ 以下 14 个目的地此前**从未注册**，但 ToolScreen / ToolboxScreen
                    //    一直在 navigate("toolbox_xxx")。Navigation 找不到目的地会抛
                    //    IllegalArgumentException，也就是说整个工具箱子页面
                    //    （分区备份 / CPU 调度 / App 管理 / 方舟 / 隐藏 / 权限审计…）
                    //    从这里点进去一律进不去。
                    // ============================================================
                    composable("toolbox_flash") { FlashScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_console") { HuijuiConsoleScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_backup") { BackupScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_cpu") { CpuControlScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_tweaks") { SystemTweaksScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_audit") { RootAuditScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_apps") { AppManagerScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_hardware") { HardwareInfoScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_sensors") { SensorTestScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_screentest") { ScreenTestScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_quickaction") { QuickActionScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_permissions") { PermissionAuditScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_ark") { ArkScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_hiding") { HidingDashboardScreen(onBack = { navController.popBackStack() }) }

                    composable("tool") {
                        ToolScreen(
                            onNavigateToConsole = { navController.navigate("toolbox_console") },
                            onNavigateToBackup = { navController.navigate("toolbox_backup") },
                            onNavigateToCpu = { navController.navigate("toolbox_cpu") },
                            onNavigateToTweaks = { navController.navigate("toolbox_tweaks") },
                            onNavigateToAudit = { navController.navigate("toolbox_audit") },
                            onNavigateToApps = { navController.navigate("toolbox_apps") },
                            onNavigateToHardware = { navController.navigate("toolbox_hardware") },
                            onNavigateToSensors = { navController.navigate("toolbox_sensors") },
                            onNavigateToScreenTest = { navController.navigate("toolbox_screentest") },
                            onNavigateToQuickAction = { navController.navigate("toolbox_quickaction") },
                            onNavigateToPermissions = { navController.navigate("toolbox_permissions") },
                            onNavigateToArk = { navController.navigate("toolbox_ark") },
                            onNavigateToHiding = { navController.navigate("toolbox_hiding") },
                            onNavigateToKernelModules = { navController.navigate("kernel_modules") }
                        )
                    }
                    composable("superuser") { SuperUserScreen() }
                    composable("module") {
                        ModuleScreen(onNavigateToWebUI = { name, path ->
                            navController.currentBackStackEntry?.savedStateHandle?.set("webName", name)
                            navController.currentBackStackEntry?.savedStateHandle?.set("webPath", path)
                            navController.navigate("module_webui")
                        })
                    }
                    // ⚠️ 旧版这个 composable 的函数体是**空的**：读出了 name/path 之后什么都没画，
                    //    所以从模块页点 WebUI 会进到一个纯黑板。这里补上真正的渲染。
                    composable("module_webui") {
                        val entry = navController.previousBackStackEntry?.savedStateHandle
                        val name = entry?.get<String>("webName") ?: "WebUI"
                        val path = entry?.get<String>("webPath").orEmpty()
                        ModuleWebUIScreen(
                            moduleName = name,
                            htmlPath = path,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("themesettings") {
                        ThemeSettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("language_settings") {
                        LanguageSettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onNavigateToTheme = { navController.navigate("themesettings") },
                            onNavigateToLanguage = { navController.navigate("language_settings") },
                            onNavigateToKernelModules = { navController.navigate("kernel_modules") },
                            onNavigateToEffects = { navController.navigate("effects_gallery") },
                            onResetComplete = {
                                navController.navigate("onboarding") {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }

            if (!hideBottomBar) {
                ModernBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/**
 * 底部导航。
 *
 * 旧版有两个明摆着的问题：
 *   1. 算了 `finalColor`（选中项要用主题色）却从来没用 —— Icon 的 tint 永远写死 Color.White，
 *      所以「选中」在视觉上只剩下字重变粗，几乎看不出来。
 *   2. 选中态没有任何背景指示器，五个图标在一排里完全分不清当前在哪一页。
 * 现在：一枚随选中项滑动的胶囊高亮 + 图标/文字颜色插值 + 按压缩放。
 */
/**
 * 底部导航。
 *
 * 相比上一版的关键改动：**指示器从「各自淡入」改成「整块滑动」**。
 * 旧实现是每个 item 里各放一枚胶囊、靠 alpha 淡入淡出 —— 切换时是「旧的一枚消失、
 * 新的一枚出现」，中间没有位移，视觉上像闪了一下。
 * 现在只画一枚胶囊，位置由 selectedIndex 插值驱动，切换时真正滑过去。
 *
 * 另外：背景材质跟随主题（HuiTheme.glass），按下有缩放与触感反馈。
 */
/**
 * 底部导航（液态气泡胶囊版）。
 *
 * 之前的滑块是一个固定尺寸的圆角矩形，从 A 平移到 B —— 硬邦邦的，没有"液体"感。
 * 现在改成真正的气泡胶囊：
 *   · 胶囊在**移动过程中会被拉长、压扁**（stretch 由当前位置与目标位置的距离驱动），
 *     停下时回弹成饱满的胶囊 —— 这就是水滴在表面张力下被拉扯再收拢的观感
 *   · 胶囊底下有一层同色的径向光晕，让它像浮在玻璃上而不是贴在上面
 *   · 顶部一道白色高光，和液态玻璃卡片呼应
 *   · 选中项才显示文字，其余只留图标，视觉更干净
 */
@Composable
fun ModernBottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val navItems = remember {
        listOf(
            NavItem("home", zakoString("主页"), Icons.Default.Home),
            NavItem("tool", zakoString("工具"), Icons.Default.Build),
            NavItem("superuser", zakoString("超管"), Icons.Default.Security),
            NavItem("module", zakoString("模块"), Icons.Default.Extension),
            NavItem("settings", zakoString("设置"), Icons.Default.Settings)
        )
    }
    val accent = AppSettings.accentColor.color
    val glass = HuiTheme.glass
    val haptic = LocalHapticFeedback.current

    val selectedIndex = navItems.indexOfFirst { it.route == currentRoute }.let { if (it < 0) 0 else it }

    // 位置用较软的弹簧，让气泡"甩"过去而不是瞬移
    val bubblePos by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 260f),
        label = "bubblePos"
    )
    // 拉伸量 = 当前位置离目标的距离；移动中拉长，到位后归零
    val stretch = kotlin.math.abs(selectedIndex.toFloat() - bubblePos).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .navigationBarsPadding()
            .height(74.dp)
            .clip(RoundedCornerShape(37.dp))
            .background(glass.fillTop.copy(alpha = 0.10f))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.11f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, glass.border.copy(alpha = 0.55f), RoundedCornerShape(37.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / navItems.size
            val cx = (bubblePos + 0.5f) * cell

            val baseW = cell * 0.66f
            val baseH = 34.dp.toPx()

            // 移动中：横向拉长、纵向压扁（体积近似守恒的水感）
            val pillW = baseW * (1f + 0.60f * stretch)
            val pillH = baseH * (1f - 0.22f * stretch)
            val topLeft = Offset(cx - pillW / 2f, (size.height - pillH) / 2f)

            // 1) 气泡下方的径向光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, size.height / 2f),
                    radius = pillW * 0.85f
                ),
                radius = pillW * 0.85f,
                center = Offset(cx, size.height / 2f)
            )

            // 2) 气泡本体
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.34f),
                        accent.copy(alpha = 0.16f),
                        accent.copy(alpha = 0.30f)
                    ),
                    startX = cx - pillW / 2f,
                    endX = cx + pillW / 2f
                ),
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f)
            )

            // 3) 顶部高光，模拟光线在液面上的反射
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                    startY = topLeft.y,
                    endY = topLeft.y + pillH * 0.55f
                ),
                topLeft = topLeft,
                size = Size(pillW, pillH * 0.55f),
                cornerRadius = CornerRadius(pillH / 2f)
            )
        }

        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val isRestricted = item.route == "superuser" && !AppSettings.hasRoot

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val iconScale by animateFloatAsState(
                    targetValue = if (isPressed && !isRestricted) 0.86f else if (isSelected) 1.10f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
                    label = "navScale_${'$'}{item.route}"
                )
                val contentColor by animateColorAsState(
                    targetValue = when {
                        isRestricted -> Color.White.copy(alpha = 0.22f)
                        isSelected -> Color.White
                        else -> Color.White.copy(alpha = 0.55f)
                    },
                    animationSpec = tween(200),
                    label = "navColor_${'$'}{item.route}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            enabled = !isRestricted,
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate(item.route)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.animateContentSize()
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.title,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp).scale(iconScale)
                        )
                        // 只有选中项才展开文字，未选中的只留图标
                        AnimatedVisibility(visible = isSelected && !isRestricted) {
                            Row {
                                Spacer(modifier = Modifier.width(6.dp))
                                ZakoText(
                                    item.title,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
