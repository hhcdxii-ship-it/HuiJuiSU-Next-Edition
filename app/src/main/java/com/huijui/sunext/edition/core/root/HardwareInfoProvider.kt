package com.huijui.sunext.edition.core.root

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 硬件信息提供者
 *
 * 数据来源：
 *   - Android API：Build / WindowManager / ActivityManager / StatFs
 *   - shell 兜底：部分厂商私有字段走 getprop
 *
 * 设计原则：
 *   - API 能拿的不走 shell（快）
 *   - 一次 exec 拉全部 getprop 兜底数据（省）
 *   - 所有数据格式化后可直接复制
 */
object HardwareInfoProvider {

    data class HardwareInfo(
        // 屏幕
        val screenResolution: String,
        val screenDensity: String,
        val screenRefreshRate: String,
        val screenSizeInch: String,
        val screenHdr: String,
        // CPU
        val cpuModel: String,
        val cpuArchitecture: String,
        val cpuCores: String,
        val cpuFreqRange: String,
        val cpuAbi: String,
        // 内存
        val ramTotal: String,
        val ramAvailable: String,
        // 存储
        val storageTotal: String,
        val storageAvailable: String,
        // 系统
        val androidVersion: String,
        val kernelVersion: String,
        val buildNumber: String,
        val securityPatch: String,
        val fingerprint: String,
        // 网络
        val wifiMac: String,
        // 传感器数量
        val sensorCount: String
    )

    suspend fun getHardwareInfo(context: Context): HardwareInfo = withContext(Dispatchers.IO) {
        // ===== 屏幕信息（API） =====
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        val widthPx = dm.widthPixels
        val heightPx = dm.heightPixels
        val densityDpi = dm.densityDpi
        val densityRatio = densityDpi / 160f
        val screenInch = runCatching {
            val w = widthPx / densityDpi.toFloat()
            val h = heightPx / densityDpi.toFloat()
            val diag = kotlin.math.sqrt(w * w + h * h)
            "%.2f".format(diag)
        }.getOrDefault("未知")

        val refreshRate = runCatching {
            val max = wm.defaultDisplay.supportedModes.maxOfOrNull { it.refreshRate } ?: 0f
            "${"%.0f".format(max)}Hz"
        }.getOrDefault("未知")

        val hdr = runCatching {
            val display = wm.defaultDisplay
            val caps = display.hdrCapabilities
            if (caps != null && caps.supportedHdrTypes.isNotEmpty()) "支持" else "不支持"
        }.getOrDefault("未知")

        // ===== CPU（API + shell） =====
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"
        val cpuCores = Runtime.getRuntime().availableProcessors().toString()

        // ===== 内存（API） =====
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramTotal = formatBytes(memInfo.totalMem)
        val ramAvailable = formatBytes(memInfo.availMem)

        // ===== 存储（API） =====
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val storageTotal = formatBytes(stat.totalBytes)
        val storageAvailable = formatBytes(stat.availableBytes)

        // ===== shell 兜底：一次拉全部 =====
        val cmd = """
            ( getprop ro.hardware 2>/dev/null | sed 's/^/HW:/' )
            ( getprop ro.board.platform 2>/dev/null | sed 's/^/PLATFORM:/' )
            ( getprop ro.soc.model 2>/dev/null | sed 's/^/SOC:/' )
            ( uname -r 2>/dev/null | sed 's/^/KERNEL:/' )
            ( getprop ro.build.display.id 2>/dev/null | sed 's/^/BUILD:/' )
            ( getprop ro.build.version.security_patch 2>/dev/null | sed 's/^/PATCH:/' )
            ( getprop ro.build.fingerprint 2>/dev/null | sed 's/^/FP:/' )
            ( getprop ro.boot.wifimacaddr 2>/dev/null | sed 's/^/MAC:/' )
            ( cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null | sed 's/^/MAXF:/' )
            ( cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq 2>/dev/null | sed 's/^/MINF:/' )
            ( cat /sys/class/thermal/thermal_zone0/type 2>/dev/null | sed 's/^/THERMAL:/' )
        """.trimIndent()

        val result = HuijuiShell.exec(cmd)
        var hardware = ""
        var platform = ""
        var soc = ""
        var kernel = Build.VERSION.RELEASE.takeIf { false } ?: ""
        var buildNumber = ""
        var securityPatch = ""
        var fingerprint = ""
        var wifiMac = ""
        var maxFreq = 0L
        var minFreq = 0L

        for (line in result.out) {
            when {
                line.startsWith("HW:") -> hardware = line.removePrefix("HW:").trim()
                line.startsWith("PLATFORM:") -> platform = line.removePrefix("PLATFORM:").trim()
                line.startsWith("SOC:") -> soc = line.removePrefix("SOC:").trim()
                line.startsWith("KERNEL:") -> kernel = line.removePrefix("KERNEL:").trim()
                line.startsWith("BUILD:") -> buildNumber = line.removePrefix("BUILD:").trim()
                line.startsWith("PATCH:") -> securityPatch = line.removePrefix("PATCH:").trim()
                line.startsWith("FP:") -> fingerprint = line.removePrefix("FP:").trim()
                line.startsWith("MAC:") -> wifiMac = line.removePrefix("MAC:").trim()
                line.startsWith("MAXF:") -> maxFreq = line.removePrefix("MAXF:").trim().toLongOrNull() ?: 0L
                line.startsWith("MINF:") -> minFreq = line.removePrefix("MINF:").trim().toLongOrNull() ?: 0L
            }
        }

        // CPU 型号组装
        val cpuModel = when {
            soc.isNotBlank() -> soc
            platform.isNotBlank() -> platform
            hardware.isNotBlank() -> hardware
            else -> Build.HARDWARE
        }

        // CPU 频率范围
        val cpuFreqRange = if (maxFreq > 0 && minFreq > 0) {
            "${minFreq / 1000}MHz - ${maxFreq / 1000}MHz"
        } else "未知"

        // 传感器数量
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensorCount = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL).size.toString()

        HardwareInfo(
            screenResolution = "$widthPx × $heightPx",
            screenDensity = "$densityDpi dpi (${"%.2f".format(densityRatio)}x)",
            screenRefreshRate = refreshRate,
            screenSizeInch = "$screenInch 英寸",
            screenHdr = hdr,
            cpuModel = cpuModel.ifBlank { "未知" },
            cpuArchitecture = System.getProperty("os.arch") ?: "未知",
            cpuCores = "$cpuCores 核心",
            cpuFreqRange = cpuFreqRange,
            cpuAbi = cpuAbi,
            ramTotal = ramTotal,
            ramAvailable = ramAvailable,
            storageTotal = storageTotal,
            storageAvailable = storageAvailable,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernelVersion = kernel.ifBlank { "未知" },
            buildNumber = buildNumber.ifBlank { Build.DISPLAY },
            securityPatch = securityPatch.ifBlank { "未知" },
            fingerprint = fingerprint.ifBlank { Build.FINGERPRINT },
            wifiMac = wifiMac.ifBlank { "无法读取（Android 6+ 限制）" },
            sensorCount = "$sensorCount 个"
        )
    }

    /**
     * 格式化硬件信息为纯文本（用于复制）
     */
    fun formatAsText(info: HardwareInfo): String = buildString {
        appendLine("=== HuiJuiSU 硬件信息 ===")
        appendLine()
        appendLine("【屏幕】")
        appendLine("分辨率：${info.screenResolution}")
        appendLine("密度：${info.screenDensity}")
        appendLine("刷新率：${info.screenRefreshRate}")
        appendLine("尺寸：${info.screenSizeInch}")
        appendLine("HDR：${info.screenHdr}")
        appendLine()
        appendLine("【处理器】")
        appendLine("型号：${info.cpuModel}")
        appendLine("架构：${info.cpuArchitecture}")
        appendLine("核心：${info.cpuCores}")
        appendLine("频率：${info.cpuFreqRange}")
        appendLine("ABI：${info.cpuAbi}")
        appendLine()
        appendLine("【内存】")
        appendLine("总内存：${info.ramTotal}")
        appendLine("可用内存：${info.ramAvailable}")
        appendLine()
        appendLine("【存储】")
        appendLine("总存储：${info.storageTotal}")
        appendLine("可用存储：${info.storageAvailable}")
        appendLine()
        appendLine("【系统】")
        appendLine("Android：${info.androidVersion}")
        appendLine("内核：${info.kernelVersion}")
        appendLine("Build：${info.buildNumber}")
        appendLine("安全补丁：${info.securityPatch}")
        appendLine("指纹：${info.fingerprint}")
        appendLine()
        appendLine("【网络】")
        appendLine("WiFi MAC：${info.wifiMac}")
        appendLine()
        appendLine("【传感器】")
        appendLine("数量：${info.sensorCount}")
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return "%.2f GB".format(gb)
    }
}
