package com.huijui.sunext.edition.core.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CheatDetector {

    data class RiskApp(
        val packageName: String,
        val appName: String,
        val reason: String
    )

    private val HACK_TOOLS = listOf(
        "gameguardian", "lucky_patcher", "frida-server",
        "com.cheat.engine", "gg.modifier", "gamehacker"
    )

    private val ONLINE_INJECTION_KEYWORDS = listOf(
        "executor", "injector", "script_hub", "aimbot", 
        "wallhack", "esp_hack", "mod_menu", "cheat_menu"
    )

    private val GENERAL_CHEAT_KEYWORDS = listOf(
        "cheat", "hack", "esp", "wallhack", "aimbot", "mod"
    )

    suspend fun scan(context: Context): List<RiskApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val risks = mutableListOf<RiskApp>()

        for (app in installedApps) {
            val pkg = app.packageName.lowercase()
            val appName = pm.getApplicationLabel(app).toString().lowercase()

            if (pkg == "com.huijui.sunext.edition" || (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            if (HACK_TOOLS.any { pkg.contains(it) }) {
                risks.add(RiskApp(app.packageName, pm.getApplicationLabel(app).toString(), "危险的内存修改/注入工具"))
                continue
            }

            // 🚀 修复：用 getPackageInfo 拿网络权限
            val hasNetwork = try {
                val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                }
                pkgInfo.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
            } catch (e: Exception) { false }

            if (hasNetwork) {
                val hasInjectionKeyword = ONLINE_INJECTION_KEYWORDS.any { pkg.contains(it) || appName.contains(it) }
                val hasGeneralCheatKeyword = GENERAL_CHEAT_KEYWORDS.any { pkg.contains(it) || appName.contains(it) }

                if (hasInjectionKeyword) {
                    risks.add(RiskApp(app.packageName, pm.getApplicationLabel(app).toString(), "联机脚本注入/执行器，严重破坏游戏平衡"))
                } else if (hasGeneralCheatKeyword && (pkg.contains("roblox") || pkg.contains("game"))) {
                    risks.add(RiskApp(app.packageName, pm.getApplicationLabel(app).toString(), "检测到联机作弊特征"))
                }
            }
        }
        risks
    }

    fun isForbidden(context: Context, packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (HACK_TOOLS.any { pkg.contains(it) }) return true
        
        val hasNetwork = try {
            val pm = context.packageManager
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            pkgInfo.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
        } catch (e: Exception) { false }

        if (hasNetwork) {
            if (ONLINE_INJECTION_KEYWORDS.any { pkg.contains(it) } || GENERAL_CHEAT_KEYWORDS.any { pkg.contains(it) }) return true
        }
        return false
    }
}
