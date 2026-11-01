package com.huijui.sunext.edition

import com.huijui.sunext.edition.core.ui.language.zakoString

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.huijui.sunext.edition.core.utils.HighRefreshRateHelper.enableMaxRefreshRate(this)
        AppSettings.init(this)
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
        currentRoute == "module_webui" ||
        currentRoute == "language_settings"
    val forceStrongGlass = currentRoute?.startsWith("toolbox_") == true || currentRoute == "module_webui"

    val languageMode = remember(AppSettings.currentLanguage) {
        try { LanguageMode.valueOf(AppSettings.currentLanguage) } catch (_: Exception) { LanguageMode.ZH_CN }
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
                            onNavigateToHiding = { navController.navigate("toolbox_hiding") }
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .navigationBarsPadding()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(36.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val isRestricted = item.route == "superuser" && !AppSettings.hasRoot

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed && !isRestricted) 0.86f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                    label = "navScale_${item.route}"
                )
                val highlight by animateFloatAsState(
                    targetValue = if (isSelected && !isRestricted) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                    label = "navHighlight_${item.route}"
                )
                val contentColor = when {
                    isRestricted -> Color.White.copy(alpha = 0.22f)
                    isSelected -> accent
                    else -> Color.White.copy(alpha = 0.62f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            enabled = !isRestricted,
                            interactionSource = interactionSource,
                            indication = null
                        ) { onNavigate(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 26.dp)
                                    .graphicsLayer { alpha = highlight * 0.9f }
                                    .background(accent.copy(alpha = 0.18f), CircleShape)
                            )
                            Icon(
                                item.icon,
                                contentDescription = item.title,
                                tint = contentColor,
                                modifier = Modifier.size(21.dp).scale(scale)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        ZakoText(
                            item.title,
                            color = contentColor,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected && !isRestricted) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
