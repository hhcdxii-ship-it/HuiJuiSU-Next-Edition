package com.huijui.sunext.edition.core.root

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ChunqiuDetector {

    data class DetectionResult(
        val isSuspicious: Boolean,
        val isDefinitelyRooted: Boolean,
        val reasons: List<String>
    )

    suspend fun comprehensiveCheck(context: Context): DetectionResult = withContext(Dispatchers.IO) {
        val reasons = mutableListOf<String>()
        var suspicious = false
        var definitelyRooted = false

        // 1. 🚀 杀手锏：通过 PackageManager 查 Root 管理器包名 (无需 Root 权限)
        val rootManagerPackages = listOf(
            "me.weishu.kernelsu", "com.topjohnwu.magisk", "com.sukisu.ultra", 
            "com.rifsxd.ksunext", "com.bakasu.ksu", "org.lsposed.manager"
        )
        val pm = context.packageManager
        for (pkg in rootManagerPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                reasons.add("包名探测: 发现 Root 管理器 ($pkg)")
                suspicious = true
                definitelyRooted = true
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // 2. 🚀 查 /proc/self/maps 里的匿名可执行内存段 (Zygisk 注入特征)
        try {
            val maps = File("/proc/self/maps").readLines()
            val rwxpCount = maps.count { it.contains("rwxp") || it.contains("r-xp") && it.contains("/memfd:") }
            if (rwxpCount > 3) { // 正常应用极少有这么多匿名可执行段
                reasons.add("内存扫描: 发现异常数量的可执行映射段 (疑似 Zygisk 注入)")
                suspicious = true
                definitelyRooted = true
            }
        } catch (_: Exception) {}

        // 3. 底层文件残留探测 (有一定概率被权限拦截，但多一层保险)
        val rootPaths = listOf(
            "/data/adb/ksu", "/data/adb/magisk", "/data/adb/ap",
            "/system/bin/su", "/system/xbin/su", "/sbin/su"
        )
        if (rootPaths.any { File(it).exists() }) {
            reasons.add("文件系统残留: 检测到Root管理器或su")
            suspicious = true
            definitelyRooted = true
        }

        DetectionResult(suspicious, definitelyRooted, reasons)
    }
}
