package com.huijui.su.next.core.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 App 管理提供者（冻结 / 解冻 / 系统 App 卸载 / 恢复）
 *
 * 核心操作全部走 `pm` 命令（无需各管理器私有 API）：
 *   - 冻结：pm disable-user --user 0 <pkg>
 *   - 解冻：pm enable <pkg>
 *   - 系统卸载：pm uninstall --user 0 <pkg>
 *   - 系统恢复：pm install-existing <pkg>
 *
 * 安全设计：
 *   - 系统 App 卸载仅移除"当前用户可见性"，系统分区不动，永远可恢复
 *   - 所有操作走 su（跨管理器统一）
 *   - 冻结/卸载前 UI 二次确认
 *   - 禁止卸载核心系统组件（黑名单保护）
 */
object AppManagerProvider {

    data class ManagedApp(
        val packageName: String,
        val appName: String,
        val appInfo: ApplicationInfo,
        val isSystem: Boolean,
        val isDisabled: Boolean,
        val isUninstalledForUser: Boolean
    )

    data class ScanResult(
        val apps: List<ManagedApp>,
        val totalCount: Int,
        val systemCount: Int,
        val userCount: Int,
        val disabledCount: Int,
        val uninstalledForUserCount: Int
    )

    /**
     * 核心系统组件黑名单：永远禁止冻结/卸载
     *
     * 这些是系统启动、电话、短信、系统UI等核心组件，
     * 冻结或卸载会导致设备无法使用（甚至无法开机）
     */
    private val CRITICAL_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.shell",
        "com.android.providers.settings",
        "com.android.providers.contacts",
        "com.android.providers.telephony",
        "com.android.providers.media",
        "com.android.server.telecom",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.inputmethod.latin",
        "com.google.android.gms",           // Google Play Services（系统级依赖）
        "com.google.android.gsf",
        "com.miui.core",
        "com.miui.system",
        "com.miui.securitycenter",          // 小米安全中心（系统级权限）
        "com.miui.powerkeeper",
        "com.miui.home",                    // 小米桌面
        "com.lbe.security.miui"
    )

    /**
     * 扫描已安装应用
     *
     * @param includeSystem 是否包含系统应用（默认 true）
     */
    suspend fun scanApps(
        context: Context,
        includeSystem: Boolean = true
    ): ScanResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        // 1. 一次 exec 拿全所有状态标记
        val cmd = """
            ( pm list packages -d 2>/dev/null | sed 's/^package://' | sed 's/^/DISABLED:/' )
            ( pm list packages -u 2>/dev/null | sed 's/^package://' | sed 's/^/UNINSTALLED:/' )
        """.trimIndent()

        val result = HuijuiShell.exec(cmd)
        val disabled = mutableSetOf<String>()
        val uninstalled = mutableSetOf<String>()

        for (line in result.out) {
            when {
                line.startsWith("DISABLED:") -> disabled.add(line.removePrefix("DISABLED:").trim())
                line.startsWith("UNINSTALLED:") -> uninstalled.add(line.removePrefix("UNINSTALLED:").trim())
            }
        }

        // 2. PackageManager 拿 appInfo
        val flags = if (includeSystem) {
            PackageManager.GET_META_DATA
        } else {
            PackageManager.GET_META_DATA
        }

        val installed = pm.getInstalledApplications(flags)
            .filter { app ->
                // 过滤条件：
                // - 有桌面入口 或 系统应用
                // - includeSystem = true 时全部显示
                val hasLauncher = pm.getLaunchIntentForPackage(app.packageName) != null
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                hasLauncher || (includeSystem && isSystem)
            }
            .map { app ->
                val pkg = app.packageName
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                ManagedApp(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(app).toString(),
                    appInfo = app,
                    isSystem = isSystem,
                    isDisabled = pkg in disabled,
                    isUninstalledForUser = pkg in uninstalled
                )
            }
            .sortedWith(
                compareByDescending<ManagedApp> { it.isDisabled || it.isUninstalledForUser }
                    .thenByDescending { it.isSystem }
                    .thenBy { it.appName.lowercase() }
            )

        ScanResult(
            apps = installed,
            totalCount = installed.size,
            systemCount = installed.count { it.isSystem },
            userCount = installed.count { !it.isSystem },
            disabledCount = installed.count { it.isDisabled },
            uninstalledForUserCount = installed.count { it.isUninstalledForUser }
        )
    }

    /**
     * 冻结 App（pm disable-user --user 0）
     */
    suspend fun freezeApp(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        if (packageName in CRITICAL_PACKAGES) {
            return@withContext Result.failure(
                Exception("$packageName 是核心系统组件，禁止冻结")
            )
        }

        val r = HuijuiShell.exec("pm disable-user --user 0 $packageName 2>&1")
        if (r.isSuccess && !r.out.joinToString("").contains("Exception")) {
            Result.success("已冻结 $packageName")
        } else {
            val msg = r.out.joinToString(" ").take(120).ifBlank { r.err.joinToString(" ").take(120) }
            Result.failure(Exception(msg.ifBlank { "冻结失败" }))
        }
    }

    /**
     * 解冻 App（pm enable）
     */
    suspend fun unfreezeApp(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec("pm enable $packageName 2>&1")
        if (r.isSuccess) {
            Result.success("已解冻 $packageName")
        } else {
            val msg = r.out.joinToString(" ").take(120).ifBlank { r.err.joinToString(" ").take(120) }
            Result.failure(Exception(msg.ifBlank { "解冻失败" }))
        }
    }

    /**
     * 系统 App 卸载（仅当前用户，pm uninstall --user 0）
     *
     * 系统分区不动，可随时通过 install-existing 恢复
     */
    suspend fun uninstallSystemApp(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        if (packageName in CRITICAL_PACKAGES) {
            return@withContext Result.failure(
                Exception("$packageName 是核心系统组件，禁止卸载")
            )
        }

        val r = HuijuiShell.exec("pm uninstall --user 0 $packageName 2>&1")
        val out = r.out.joinToString(" ")
        if (out.contains("Success") || (r.isSuccess && !out.contains("Exception"))) {
            Result.success("已从当前用户卸载 $packageName")
        } else {
            Result.failure(Exception(out.take(120).ifBlank { "卸载失败" }))
        }
    }

    /**
     * 系统 App 恢复（pm install-existing）
     */
    suspend fun restoreSystemApp(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec("pm install-existing $packageName 2>&1")
        if (r.isSuccess) {
            Result.success("已恢复 $packageName")
        } else {
            val msg = r.out.joinToString(" ").take(120).ifBlank { r.err.joinToString(" ").take(120) }
            Result.failure(Exception(msg.ifBlank { "恢复失败" }))
        }
    }

    /**
     * 是否核心组件（UI 层判断用）
     */
    fun isCritical(packageName: String): Boolean = packageName in CRITICAL_PACKAGES
}
