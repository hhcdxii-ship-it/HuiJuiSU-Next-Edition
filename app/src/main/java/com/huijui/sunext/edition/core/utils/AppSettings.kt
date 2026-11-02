package com.huijui.sunext.edition.core.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 卡片材质。
 *
 * LIQUID_GLASS 液态玻璃：高透 + 流动的高光带 + 硬朗亮边，模拟液体表面的张力弧。
 * SOFT_GLASS   柔光玻璃：低对比、大范围柔化、极淡描边，适合长时间盯着看的场景。
 */
enum class MaterialStyle { LIQUID_GLASS, SOFT_GLASS, GLASS, SOLID, NONE }
enum class AnimationStyle { SPRING, SMOOTH, NONE }
enum class WallpaperType { VIDEO, IMAGE, NONE }
enum class WallpaperScaleMode {
    FIT,     // 适应（完整显示，可能有黑边）
    FILL,    // 拉伸（铺满全屏，可能会变形）
    ZOOM     // 裁切（填满全屏，裁切边缘）
}
enum class AccentColor(val color: Color, val displayName: String) {
    GREEN(Color(0xFF81C784), "极客绿"),
    CYBER_BLUE(Color(0xFF4FC3F7), "赛博蓝"),
    FLAME_RED(Color(0xFFE57373), "烈焰红"),
    PURE_WHITE(Color(0xFFFFFFFF), "纯白")
}

object AppSettings {
    private const val PREF_NAME = "huijui_next_prefs"
    private lateinit var prefs: SharedPreferences

    // SupervisorJob：以前任何一个写入协程抛异常，都会把整个 saveScope 连带取消，
    // 之后所有设置保存静默失效（重启 App 才发现全丢了）。
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private var saveJob: Job? = null

    var isXiaomiDevice by mutableStateOf(false); private set
    var isHyperOs by mutableStateOf(false); private set
    var osMajorVersion by mutableStateOf("Android"); private set

    /**
     * 模块更新检查开关。
     *
     * 注：这里原本还有 isCheckManagerUpdateEnabled / isHideSelinuxEnabled /
     * isHideRootEnabled / isAntiWallpaperResetEnabled 四个字段，
     * 它们全项目**只有写入没有读取**，对应的功能从来没实现过，已删除。
     * 保留开关就要能生效，否则设置项就是在骗人。
     */
    var isCheckModuleUpdateEnabled by mutableStateOf(true)
    var isAntiRootLossEnabled by mutableStateOf(false)
    var isAntiDevOptionsEnabled by mutableStateOf(false)

    var materialStyle by mutableStateOf(MaterialStyle.GLASS)
    var animationStyle by mutableStateOf(AnimationStyle.SPRING)
    var wallpaperType by mutableStateOf(WallpaperType.VIDEO)
    // 默认 ZOOM（裁切铺满）—— 之前是 FIT，会留黑边，不像壁纸像一张贴在黑底上的图
    var wallpaperScaleMode by mutableStateOf(WallpaperScaleMode.ZOOM)

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

    var systemMonitorEnabled by mutableStateOf(false)
    var rootAuditEnabled by mutableStateOf(false)

    /** 作弊哨兵：默认开启。判定条件见 CheatSentinel，只装性能模块的用户不会被误伤。 */
    var cheatSentinelEnabled by mutableStateOf(true)

    /** 自爆之后的环境锁（毫秒时间戳），锁定期内不接受重新启用。 */
    var cheatLockUntil by mutableStateOf(0L)

    /** 累计自爆次数，用于在设置页给出可见的后果。 */
    var cheatPurgeCount by mutableStateOf(0)

    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        isXiaomiDevice = RomUtils.isXiaomi()
        isHyperOs = RomUtils.isHyperOs()
        osMajorVersion = RomUtils.getOsMajorVersion()

        currentLanguage = prefs.getString("current_language", "ZH_CN") ?: "ZH_CN"
        isLauncherLandscape = prefs.getBoolean("launcher_landscape", false)
        isCheckModuleUpdateEnabled = prefs.getBoolean("check_module", true)
        isAntiRootLossEnabled = prefs.getBoolean("anti_root_loss", false)
        isAntiDevOptionsEnabled = prefs.getBoolean("anti_dev_options", false)

        materialStyle = try { MaterialStyle.valueOf(prefs.getString("material_style", MaterialStyle.GLASS.name) ?: MaterialStyle.GLASS.name) } catch (_: Exception) { MaterialStyle.GLASS }
        animationStyle = try { AnimationStyle.valueOf(prefs.getString("animation_style", AnimationStyle.SPRING.name) ?: AnimationStyle.SPRING.name) } catch (_: Exception) { AnimationStyle.SPRING }
        wallpaperType = try { WallpaperType.valueOf(prefs.getString("wallpaper_type", WallpaperType.VIDEO.name) ?: WallpaperType.VIDEO.name) } catch (_: Exception) { WallpaperType.VIDEO }
        wallpaperScaleMode = try {
            WallpaperScaleMode.valueOf(
                prefs.getString("wallpaper_scale_mode", WallpaperScaleMode.ZOOM.name)
                    ?: WallpaperScaleMode.ZOOM.name
            )
        } catch (_: Exception) { WallpaperScaleMode.ZOOM }

        // 一次性迁移：老版本默认是 FIT，壁纸会留黑边。已经装过的用户在升级后
        // 也该看到铺满的效果，否则"改为全屏"这个预期落不到他们身上。
        if (!prefs.getBoolean("wallpaper_fullscreen_migrated", false)) {
            if (wallpaperScaleMode == WallpaperScaleMode.FIT) {
                wallpaperScaleMode = WallpaperScaleMode.ZOOM
            }
            prefs.edit().putBoolean("wallpaper_fullscreen_migrated", true).apply()
        }
        accentColor = try { AccentColor.valueOf(prefs.getString("accent_color", AccentColor.GREEN.name) ?: AccentColor.GREEN.name) } catch (_: Exception) { AccentColor.GREEN }

        cardCornerRadius = prefs.getFloat("card_corner_radius", 24f)
        wallpaperDimAlpha = prefs.getFloat("wallpaper_dim", 0.2f)
        animationSpeedScale = prefs.getFloat("anim_speed", 1.0f)

        customVideoPath = prefs.getString("custom_video", null)
        customImagePath = prefs.getString("custom_image", null)
        grantedApps = prefs.getStringSet("granted_apps", emptySet())?.toSet() ?: emptySet()

        onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        defaultLauncherMode = prefs.getBoolean("default_launcher_mode", true)
        systemMonitorEnabled = prefs.getBoolean("system_monitor_enabled", false)
        rootAuditEnabled = prefs.getBoolean("root_audit_enabled", false)
        cheatSentinelEnabled = prefs.getBoolean("cheat_sentinel", true)
        cheatLockUntil = prefs.getLong("cheat_lock_until", 0L)
        cheatPurgeCount = prefs.getInt("cheat_purge_count", 0)

        isInitialized = true
    }

    /**
     * 异步保存。
     *
     * ⚠️ 旧实现是这么写的：
     * ```
     * if (isSaving.getAndSet(true)) return   // 并发时直接丢弃这次保存
     * ```
     * 也就是说「正在写」的那一瞬间进来的保存请求会被**静默丢掉**。
     * 而它内部的 prefs.edit() 是在协程里读字段的 —— 读写时序一旦错开，
     * 用户刚改的那一项就没写进磁盘。典型症状：设置改完，杀进程再进，值又变回去了。
     *
     * 现在改成「防抖 + 互斥 + 每次都写整快照」：
     *   - 60ms 内的连续调用合并成一次（拖动滑杆不会打爆磁盘）；
     *   - 永远以最新字段值写一份完整快照，后写覆盖前写，不会丢；
     *   - 用 SupervisorJob，单次写失败不会把整个保存通道废掉。
     */
    fun save() {
        if (!::prefs.isInitialized) return
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(60)
            saveMutex.withLock { writeSnapshot() }
        }
    }

    private fun writeSnapshot() {
        prefs.edit().apply {
            putString("current_language", currentLanguage)
            putBoolean("launcher_landscape", isLauncherLandscape)
            putBoolean("check_module", isCheckModuleUpdateEnabled)
            putBoolean("anti_root_loss", isAntiRootLossEnabled)
            putBoolean("anti_dev_options", isAntiDevOptionsEnabled)
            putString("material_style", materialStyle.name)
            putString("animation_style", animationStyle.name)
            putString("wallpaper_type", wallpaperType.name)
            putString("wallpaper_scale_mode", wallpaperScaleMode.name)
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
            putBoolean("cheat_sentinel", cheatSentinelEnabled)
            putLong("cheat_lock_until", cheatLockUntil)
            putInt("cheat_purge_count", cheatPurgeCount)
            apply()
        }
    }

    fun clearCustomWallpaper(context: Context) {
        customVideoPath?.let { java.io.File(it).delete() }
        customImagePath?.let { java.io.File(it).delete() }
        customVideoPath = null
        customImagePath = null
        // 🚀 恢复默认时，直接切回内置视频（初音未来），不要变纯黑
        wallpaperType = WallpaperType.VIDEO
        save()
    }

    // 🚀 并发安全的集合修改
    @Synchronized
    fun updateGrantedApp(packageName: String, grant: Boolean) {
        grantedApps = if (grant) grantedApps + packageName else grantedApps - packageName
        save()
    }
}
