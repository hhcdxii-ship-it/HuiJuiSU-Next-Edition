package com.huijui.su.next.core.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 🚀 恢复出厂设置管理器（方案 B：保留 Root 授权，重置 UI 配置）
 *
 * 设计原则：
 *   1. 清 SharedPreferences（除了 grantedApps）
 *   2. 清缓存目录（壁纸文件、临时文件、刷入缓存）
 *   3. 重置所有 AppSettings 字段到默认值（手动赋值，不走 init 重读 ROM）
 *   4. onboardingCompleted = false（重置后回引导页）
 *   5. 保留 grantedApps（用户辛苦一个个点出来的，不能丢）
 *
 * 为什么手动重置字段而不走 AppSettings.init()：
 *   - init 会重新调 RomUtils.isXiaomi() / isHyperOs()，浪费 IO
 *   - init 会把 grantedApps 读成空（prefs 已清），还得再写回来
 *   - 手动赋值最直白，一眼看清重置了哪些字段
 *
 * 线程约定：
 *   - 文件操作在 Dispatchers.IO
 *   - AppSettings 字段赋值在 Dispatchers.Main（Compose state 要主线程）
 */
object AppResetManager {

    data class ResetResult(
        val success: Boolean,
        val message: String
    )

    suspend fun resetToFactory(context: Context): ResetResult = withContext(Dispatchers.IO) {
        try {
            // 1. 备份要保留的字段
            val preservedGrantedApps = AppSettings.grantedApps

            // 2. 清缓存
            clearCaches(context)

            // 3. 清 SharedPreferences（同步 commit，确保在重置字段前落盘）
            context
                .getSharedPreferences("huijui_next_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()

            // 4. 重置所有 AppSettings 字段到默认值
            withContext(Dispatchers.Main) {
                AppSettings.isCheckManagerUpdateEnabled = true
                AppSettings.isCheckModuleUpdateEnabled = true
                AppSettings.isHideSelinuxEnabled = false
                AppSettings.isHideRootEnabled = true
                AppSettings.isAntiRootLossEnabled = false
                AppSettings.isAntiDevOptionsEnabled = false
                AppSettings.isAntiWallpaperResetEnabled = false

                AppSettings.materialStyle = MaterialStyle.GLASS
                AppSettings.animationStyle = AnimationStyle.SPRING
                AppSettings.wallpaperType = WallpaperType.VIDEO

                AppSettings.cardCornerRadius = 24f
                AppSettings.accentColor = AccentColor.GREEN
                AppSettings.wallpaperDimAlpha = 0.2f
                AppSettings.animationSpeedScale = 1.0f

                AppSettings.customVideoPath = null
                AppSettings.customImagePath = null

                AppSettings.currentLanguage = "ZAKO"
                AppSettings.isLauncherLandscape = false
                AppSettings.defaultLauncherMode = true

                // 关键：重置后回引导页
                AppSettings.onboardingCompleted = false

                // 保留授权记录
                AppSettings.grantedApps = preservedGrantedApps

                // 5. 落盘
                AppSettings.save()
            }

            ResetResult(true, "已恢复出厂设置")
        } catch (e: Exception) {
            e.printStackTrace()
            ResetResult(false, "重置失败：${e.message ?: "Unknown Error"}")
        }
    }

    /**
     * 清缓存：
     *   - cacheDir 下所有文件（视频缩略图、刷入缓存、临时解压目录等）
     *   - filesDir 下的自定义壁纸（custom_bg.mp4 / custom_bg.jpg）
     */
    private fun clearCaches(context: Context) {
        runCatching {
            context.cacheDir.listFiles()?.forEach { file ->
                runCatching { file.deleteRecursively() }
            }
        }
        runCatching {
            File(context.filesDir, "custom_bg.mp4").let { if (it.exists()) it.delete() }
        }
        runCatching {
            File(context.filesDir, "custom_bg.jpg").let { if (it.exists()) it.delete() }
        }
    }
}
