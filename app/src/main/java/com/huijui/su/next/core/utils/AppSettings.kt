package com.huijui.su.next.core.utils
import com.huijui.su.next.core.utils.AppSettings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.io.File

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

    // 🚀 ROM 状态
    var isXiaomiDevice by mutableStateOf(false)
        private set
    var isHyperOs by mutableStateOf(false)
        private set
    var osMajorVersion by mutableStateOf("Android")
        private set

    // 基础状态
    var isCheckManagerUpdateEnabled by mutableStateOf(true)
    var isCheckModuleUpdateEnabled by mutableStateOf(true)
    var isHideSelinuxEnabled by mutableStateOf(false)
    var isHideRootEnabled by mutableStateOf(true)

    // 🚀 防丢失与系统守护
    var isAntiRootLossEnabled by mutableStateOf(false)
    var isAntiDevOptionsEnabled by mutableStateOf(false)
    var isAntiWallpaperResetEnabled by mutableStateOf(false)

    // 外观配置
    var materialStyle by mutableStateOf(MaterialStyle.GLASS)
    var animationStyle by mutableStateOf(AnimationStyle.SPRING)
    var wallpaperType by mutableStateOf(WallpaperType.VIDEO)
    
    var cardCornerRadius by mutableStateOf(24f)
    var accentColor by mutableStateOf(AccentColor.GREEN)
    var wallpaperDimAlpha by mutableStateOf(0.6f)
    var animationSpeedScale by mutableStateOf(1.0f)

    // 自定义壁纸
    var customVideoPath by mutableStateOf<String?>(null)
    var customImagePath by mutableStateOf<String?>(null)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // 🚀 检测系统版本
        isXiaomiDevice = RomUtils.isXiaomi()
        isHyperOs = RomUtils.isHyperOs()
        osMajorVersion = RomUtils.getOsMajorVersion()

        isCheckManagerUpdateEnabled = prefs.getBoolean("check_manager", true)
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
        accentColor = AccentColor.valueOf(prefs.getString("accent_color", AccentColor.GREEN.name) ?: AccentColor.GREEN.displayName)
        wallpaperDimAlpha = prefs.getFloat("wallpaper_dim_alpha", 0.6f)
        animationSpeedScale = prefs.getFloat("animation_speed", 1.0f)

        customVideoPath = prefs.getString("custom_video_path", null)
        customImagePath = prefs.getString("custom_image_path", null)
    }

    fun save() {
        prefs.edit().apply {
            putBoolean("check_manager", isCheckManagerUpdateEnabled)
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
            putFloat("wallpaper_dim_alpha", wallpaperDimAlpha)
            putFloat("animation_speed", animationSpeedScale)
            putString("custom_video_path", customVideoPath)
            putString("custom_image_path", customImagePath)
            apply()
        }
    }

    fun clearCustomWallpaper(context: Context) {
        customVideoPath?.let { File(it).delete() }
        customImagePath?.let { File(it).delete() }
        customVideoPath = null
        customImagePath = null
        wallpaperType = WallpaperType.NONE
        save()
    }
}
