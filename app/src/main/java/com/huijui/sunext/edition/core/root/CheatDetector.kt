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

    /**
     * 是否禁止向该应用下发 Root 授权。
     *
     * ⚠️ 旧实现是「有 INTERNET 权限 + 包名/应用名里含 mod|esp|hack」就拒绝。
     * `"mod"` 和 `"esp"` 是三个字母的通用词：`com.modern.*`、任何 `*module*`、
     * 西语应用里的 `esp*` 全中招 —— 这会把大量正常应用挡在授权之外，
     * 而它恰恰接在 HuijuiDeviceBridge.grantRootToApp() 的最前面。
     *
     * 现在只用**具体的作弊工具签名**，不再做泛化单词匹配。
     */
    fun isForbidden(context: Context, packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg == "com.huijui.sunext.edition") return false
        if (pkg in NEVER_BLOCK) return false
        return FORBIDDEN_SIGNATURES.any { pkg.contains(it) }
    }

    /** 明确不许授权的作弊/注入工具，只用限定签名。 */
    private val FORBIDDEN_SIGNATURES = listOf(
        "com.zhiliao.gameguardian",
        "com.gameguardian",
        "ru.sxbuid.luckypatcher",
        "com.lucky.patcher",
        "org.frida.server",
        "frida-server",
        "com.cheat.engine",
        "gg.modifier",
        "com.gamehacker",
        "com.aimbot",
        "com.wallhack",
        "modmenu",
        "cheatmenu",
        "com.roblox.scriptexecutor"
    )

    /** 永远不拦：系统组件与已知正常工具。 */
    private val NEVER_BLOCK = setOf(
        "com.android.shell",
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "com.termux"
    )
}
