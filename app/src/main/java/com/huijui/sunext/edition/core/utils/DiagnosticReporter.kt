package com.huijui.sunext.edition.core.utils

import android.content.Context
import android.os.Build
import com.huijui.sunext.edition.core.root.DeviceInfoProvider
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.root.ModuleManager
import com.huijui.sunext.edition.core.root.RootStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断报告。
 *
 * 用户遇到问题时（模块装不上、Root 掉了、刷机失败），最需要的是把现场一次性
 * 说清楚。此前这些信息散落在六七个页面里，只能靠用户手动截图。
 *
 * 这里汇总成一段纯文本，通过系统分享面板发出去 —— 不写外部存储、不需要任何权限。
 */
object DiagnosticReporter {

    suspend fun build(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun line(s: String = "") = sb.append(s).append('\n')

        line("HuiJuiSU-Next-Edition 诊断报告")
        line("生成时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        line("=".repeat(46))
        line()

        // ── 应用 ──
        line("[应用]")
        runCatching {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            line("版本: " + (pi.versionName ?: "?"))
            line("包名: " + context.packageName)
            line("构建: " + (pi.applicationInfo?.let { "debug=" + ((it.flags and 2) != 0) } ?: "?"))
        }.onFailure { line("读取包信息失败: " + it.message) }
        line()

        // ── 系统 ──
        line("[系统]")
        line("品牌/型号: " + Build.MANUFACTURER + " " + Build.MODEL)
        line("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        line("ROM: " + (AppSettings.osMajorVersion.ifBlank { "未知" }))
        line("指纹: " + Build.FINGERPRINT)
        line("内核: " + runCatching { System.getProperty("os.version") }.getOrDefault("?"))
        line("SELinux: " + huijuiExec("getenforce"))
        line("ABI: " + Build.SUPPORTED_ABIS.joinToString(", "))
        line()

        // ── Root ──
        line("[Root]")
        line("su 可用: " + runCatching { HuijuiShell.isRooted() }.getOrDefault(false))
        runCatching {
            val st = RootStatusRepository.status.value
            line("状态: " + st::class.simpleName + " -> " + st.toString().take(200))
        }.onFailure { line("读取 Root 状态失败: " + it.message) }
        line("用户态看 /dev/huijui: " + File("/dev/huijui").exists())
        line("用户态看 /data/adb/huijui: " + File("/data/adb/huijui").exists())
        line()

        // ── 模块 ──
        line("[模块]")
        runCatching {
            val mods = ModuleManager.scanModules()
            line("数量: " + mods.size)
            mods.forEach { line("  - " + it.name + " v" + it.version + " (" + it.author + ") " + if (it.isEnabled) "启用" else "停用") }
        }.onFailure { line("扫描模块失败: " + it.message) }
        line()

        // ── 设备信息（合并一次 exec）──
        line("[设备信息]")
        runCatching {
            val d = DeviceInfoProvider.get(forceRefresh = true)
            line("brand=" + d.brand + " model=" + d.model + " kernel=" + d.kernelVer + " selinux=" + d.selinux)
        }.onFailure { line("批量读取失败: " + it.message) }
        line()

        // ── 崩溃日志 ──
        line("[最近崩溃]")
        runCatching {
            val f = File(context.filesDir, "hui_crash.log")
            if (f.exists()) line(f.readText().takeLast(3000)) else line("无记录")
        }.onFailure { line("读取失败: " + it.message) }

        sb.toString()
    }

    private suspend fun huijuiExec(cmd: String): String =
        runCatching { HuijuiShell.exec(cmd).out.joinToString(" ").trim() }.getOrDefault("?").ifBlank { "?" }
}
