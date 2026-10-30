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

        // 1. 包名检测 (无需 Root)
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

        // 2. Maps 内存扫描 (反 Zygisk，无需 Root)
        runCatching {
            val maps = File("/proc/self/maps").readLines()
            val hookKeywords = listOf("zygisk", "lsposed", "frida", "riru", "susfs")
            if (maps.any { line -> hookKeywords.any { line.contains(it, ignoreCase = true) } }) {
                reasons.add("内存扫描: 发现 Hook 框架特征")
                suspicious = true
                definitelyRooted = true
            }
        }

        // 3. 物理文件残留探测 (需要 Root 权限通道才能有效读取)
        runCatching {
            val checkCmd = "su -c 'ls /data/adb 2>/dev/null || echo none'"
            val r = HuijuiShell.exec(checkCmd, useCache = false, timeoutMs = 3000L)
            if (r.isSuccess) {
                val dirs = r.out.joinToString(" ")
                if (dirs.contains("ksu") || dirs.contains("magisk") || dirs.contains("ap") || dirs.contains("modules")) {
                    reasons.add("物理残留: 检测到 Root 目录")
                    suspicious = true
                    definitelyRooted = true
                }
            }
        }

        DetectionResult(suspicious, definitelyRooted, reasons)
    }
}
