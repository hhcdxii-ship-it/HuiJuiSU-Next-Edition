package com.huijui.sunext.edition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.ui.component.LocalStrongGlass
import com.huijui.sunext.edition.core.ui.component.SmartBackground
import com.huijui.sunext.edition.core.ui.language.LanguageMode
import com.huijui.sunext.edition.core.ui.language.LocalLanguageMode
import com.huijui.sunext.edition.core.ui.language.ZakoText
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.huijui.sunext.edition.core.utils.HighRefreshRateHelper.enableMaxRefreshRate(this)
        AppSettings.init(this)
        setContent { MainApp() }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val scope = rememberCoroutineScope()

    // 生命周期感知：切回前台重新检查 Root
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch(Dispatchers.IO) {
                    // 🚀 闭环：重启后强制清理缓存，重新检测 Root 状态
                    HuijuiShell.release()
                    val rooted = HuijuiShell.isRooted()
                    withContext(Dispatchers.Main) { 
                        AppSettings.hasRoot = rooted 
                        // 刷新触发主页状态重载
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hideBottomBar = currentRoute == "onboarding" || currentRoute?.startsWith("toolbox_") == true || currentRoute == "module_webui" || currentRoute == "language_settings"
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
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = (AppSettings.wallpaperDimAlpha * 1.2f).coerceIn(0f, 1f))))
            }

            Box(
                modifier = Modifier.fillMaxSize()
                    .then(if (currentRoute == "onboarding") Modifier else Modifier.statusBarsPadding())
                    .padding(bottom = if (hideBottomBar) 0.dp else 100.dp)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (AppSettings.onboardingCompleted) "home" else "onboarding",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
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
                    composable("module_webui") {
                        val name = navController.previousBackStackEntry?.savedStateHandle?.get<String>("webName") ?: "WebUI"
                        val path = navController.previousBackStackEntry?.savedStateHandle?.get<String>("webPath") ?: ""
                        if (path.isNotEmpty()) ModuleWebUIScreen(name, path, onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onNavigateToTheme = { navController.navigate("themesettings") },
                            onNavigateToLanguage = { navController.navigate("language_settings") },
                            onResetComplete = { navController.navigate("onboarding") { popUpTo(navController.graph.id) { inclusive = true } } }
                        )
                    }
                    composable("themesettings") { ThemeSettingsScreen(onBack = { navController.popBackStack() }) }
                    composable("language_settings") { LanguageSettingsScreen(onBack = { navController.popBackStack() }) }

                    composable("toolbox_console") { HuijuiConsoleScreen(onBack = { navController.popBackStack() }) }
                    composable("toolbox_flash") { FlashScreen(onBack = { navController.popBackStack() }) }
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
                }
            }

            if (!hideBottomBar) {
                ModernBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun ModernBottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val navItems = remember {
        listOf(
            NavItem("home", "主页", Icons.Default.Home),
            NavItem("tool", "工具", Icons.Default.Build),
            NavItem("superuser", "超管", Icons.Default.Security),
            NavItem("module", "模块", Icons.Default.Extension),
            NavItem("settings", "设置", Icons.Default.Settings)
        )
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp).navigationBarsPadding().height(72.dp)
            .clip(RoundedCornerShape(36.dp)).background(Color.Black.copy(0.65f)).border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(36.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.route
            val isRestricted = (item.route == "superuser") && !AppSettings.hasRoot
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed && !isRestricted) 0.8f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f), label = "scale")
            val finalColor = when { isRestricted -> Color.White.copy(0.2f); isSelected -> AppSettings.accentColor.color; else -> Color.White.copy(0.6f) }

            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(enabled = !isRestricted, interactionSource = interactionSource, indication = null) { onNavigate(item.route) }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, null, tint = finalColor, modifier = Modifier.size(22.dp).scale(scale))
                    Spacer(modifier = Modifier.height(2.dp))
                    ZakoText(item.title, color = finalColor, fontSize = 10.sp, fontWeight = if (isSelected && !isRestricted) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
