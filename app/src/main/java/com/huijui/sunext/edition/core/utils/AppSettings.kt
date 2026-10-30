package com.huijui.sunext.edition.core.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class MaterialStyle { GLASS, SOLID, NONE }
enum class AnimationStyle { SPRING, SMOOTH, NONE }
enum class WallpaperType { VIDEO, IMAGE, NONE }
enum class AccentColor(val color: Color, val displayName: String) {
    GREEN(Color(0xFF81C784), "极客绿"),
    CYBER_BLUE(Color(0xFF4FC3F7), "赛博蓝"),
    FLAME_RED(Color(0xFFE57373), "烈焰红"),
    PURE_WHITE(Color(0xFFFFFFFF), "纯白")
}

object AppSettings {
    private const val PREF_NAME = "huijui_next_prefs"
    private lateinit var prefs: SharedPreferences

    var isXiaomiDevice by mutableStateOf(false)
        private set
    var isHyperOs by mutableStateOf(false)
        private set
    var osMajorVersion by mutableStateOf("Android")
        private set

    var isCheckManagerUpdateEnabled by mutableStateOf(true)
    var isCheckModuleUpdateEnabled by mutableStateOf(true)
    var isHideSelinuxEnabled by mutableStateOf(false)
    var isHideRootEnabled by mutableStateOf(true)

    var isAntiRootLossEnabled by mutableStateOf(false)
    var isAntiDevOptionsEnabled by mutableStateOf(false)
    var isAntiWallpaperResetEnabled by mutableStateOf(false)

    var materialStyle by mutableStateOf(MaterialStyle.GLASS)
    var animationStyle by mutableStateOf(AnimationStyle.SPRING)
    var wallpaperType by mutableStateOf(WallpaperType.VIDEO)

    var cardCornerRadius by mutableStateOf(24f)
    var accentColor by mutableStateOf(AccentColor.GREEN)
    var wallpaperDimAlpha by mutableStateOf(0.2f)
    var animationSpeedScale by mutableStateOf(1.0f)

    var customVideoPath by mutableStateOf<String?>(null)
    var customImagePath by mutableStateOf<String?>(null)

    var grantedApps by mutableStateOf(setOf<String>())
    var isInitialized by mutableStateOf(false)
    var isLauncherLandscape by mutableStateOf(false)
    var currentLanguage by mutableStateOf("ZH_CN")
    var hasRoot by mutableStateOf(false)

    var onboardingCompleted by mutableStateOf(false)
    var defaultLauncherMode by mutableStateOf(true)

    // 🚀 M1: 系统监控通知开关
    var systemMonitorEnabled by mutableStateOf(false)

    // 🚀 M3: Root 审计通知开关
    var rootAuditEnabled by mutableStateOf(false)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        isXiaomiDevice = RomUtils.isXiaomi()
        isHyperOs = RomUtils.isHyperOs()
        osMajorVersion = RomUtils.getOsMajorVersion()

        isCheckManagerUpdateEnabled = prefs.getBoolean("check_manager", true)
        currentLanguage = prefs.getString("current_language", "ZH_CN") ?: "ZH_CN"
        isLauncherLandscape = prefs.getBoolean("launcher_landscape", false)
        isCheckModuleUpdateEnabled = prefs.getBoolean("check_module", true)
        isHideSelinuxEnabled = prefs.getBoolean("hide_selinux", false)
        isHideRootEnabled = prefs.getBoolean("hide_root", true)
        isAntiRootLossEnabled = prefs.getBoolean("anti_root_loss", false)
        isAntiDevOptionsEnabled = prefs.getBoolean("anti_dev_options", false)
        isAntiWallpaperResetEnabled = prefs.getBoolean("anti_wallpaper_reset", false)

        materialStyle = MaterialStyle.valueOf(prefs.getString("material_style", MaterialStyle.GLASS.name) ?: MaterialStyle.GLASS.name)
        animationStyle = AnimationStyle.valueOf(prefs.getString("animation_style", AnimationStyle.SPRING.name) ?: AnimationStyle.SPRING.name)
        wallpaperType = WallpaperType.valueOf(prefs.getString("wallpaper_type", WallpaperType.VIDEO.name) ?: WallpaperType.VIDEO.name)

        cardCornerRadius = prefs.getFloat("card_corner_radius", 24f)
        accentColor = AccentColor.valueOf(prefs.getString("accent_color", AccentColor.GREEN.name) ?: AccentColor.GREEN.name)
        wallpaperDimAlpha = prefs.getFloat("wallpaper_dim", 0.2f)
        animationSpeedScale = prefs.getFloat("anim_speed", 1.0f)

        customVideoPath = prefs.getString("custom_video", null)
        customImagePath = prefs.getString("custom_image", null)

        val grantedList = prefs.getStringSet("granted_apps", emptySet()) ?: emptySet()
        grantedApps = grantedList.toSet()

        onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        defaultLauncherMode = prefs.getBoolean("default_launcher_mode", true)

        // 🚀 M1: 监控开关
        systemMonitorEnabled = prefs.getBoolean("system_monitor_enabled", false)

        // 🚀 M3: 审计通知开关
        rootAuditEnabled = prefs.getBoolean("root_audit_enabled", false)

        isInitialized = true
    }

    fun save() {
        prefs.edit().apply {
            putBoolean("check_manager", isCheckManagerUpdateEnabled)
            putString("current_language", currentLanguage)
            putBoolean("launcher_landscape", isLauncherLandscape)
            putBoolean("check_module", isCheckModuleUpdateEnabled)
            putBoolean("hide_selinux", isHideSelinuxEnabled)
            putBoolean("hide_root", isHideRootEnabled)
            putBoolean("anti_root_loss", isAntiRootLossEnabled)
            putBoolean("anti_dev_options", isAntiDevOptionsEnabled)
            putBoolean("anti_wallpaper_reset", isAntiWallpaperResetEnabled)
            putString("material_style", materialStyle.name)
            putString("animation_style", animationStyle.name)
            putString("wallpaper_type", wallpaperType.name)
            putFloat("card_corner_radius", cardCornerRadius)
            putString("accent_color", accentColor.name)
            putFloat("wallpaper_dim", wallpaperDimAlpha)
            putFloat("anim_speed", animationSpeedScale)
            putString("custom_video", customVideoPath)
            putString("custom_image", customImagePath)
            putStringSet("granted_apps", grantedApps)
            putBoolean("onboarding_completed", onboardingCompleted)
            putBoolean("default_launcher_mode", defaultLauncherMode)
            putBoolean("system_monitor_enabled", systemMonitorEnabled)
            putBoolean("root_audit_enabled", rootAuditEnabled)
            apply()
        }
    }

    fun clearCustomWallpaper(context: Context) {
        customVideoPath?.let { java.io.File(it).delete() }
        customImagePath?.let { java.io.File(it).delete() }
        customVideoPath = null
        customImagePath = null
        wallpaperType = WallpaperType.NONE
        save()
    }
}
