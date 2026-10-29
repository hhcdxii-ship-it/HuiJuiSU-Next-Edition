package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 Root 审计数据提供者
 *
 * 数据来源（双路，兼容所有管理器）：
 *   1. logcat：抓所有管理器的 su 请求日志
 *   2. AppSettings.grantedApps：已授权应用清单
 *
 * 为什么不解析管理器私有数据库：
 *   - Magisk magisk.db schema 在 27.x 改过多次
 *   - KernelSU / APatch 日志格式不统一
 *   - logcat 是唯一所有管理器共用的 stdout 通道
 *
 * 设计原则：
 *   - 只读（不修改任何管理器状态）
 *   - 正则宽松匹配（各种管理器的日志格式差异大）
 *   - 时间倒序（最新在前）
 */
object RootAuditProvider {

    data class AuditEntry(
        val timestamp: Long,           // 毫秒
        val packageName: String,       // 请求 Root 的包名
        val appName: String,           // 应用名（能查到就填，查不到用包名）
        val uid: Int,                  // UID（未知填 -1）
        val granted: Boolean,          // 是否授权
        val source: String,            // 来源（HUI/KSU/Magisk/logcat）
        val rawLog: String             // 原始日志行
    )

    data class AuditReport(
        val entries: List<AuditEntry>,
        val managerName: String,       // 当前管理器
        val totalRequests: Int,        // 本次拉取的总条数
        val dataSource: String,        // "logcat" / "grantedList"
        val logcatAvailable: Boolean   // logcat 是否可用
    )

    /**
     * 拉取最近 50 条 su 审计日志
     *
     * @param maxLines 最多返回多少条（默认 50，防止 UI 卡顿）
     */
    suspend fun fetchRecentAudits(maxLines: Int = 50): AuditReport = withContext(Dispatchers.IO) {
        val manager = RootManagerDetector.detect()
        val managerName = manager.shortName

        // ===== 1. 先拉 logcat =====
        val logcatCmd = """
            logcat -d -t 300 2>/dev/null | grep -iE "su:|grant.*root|root.*grant|superuser|huijui" | tail -n $maxLines
        """.trimIndent()

        val logcatResult = HuijuiShell.exec(logcatCmd, timeoutMs = 5000L)
        val logcatAvailable = logcatResult.isSuccess && logcatResult.out.isNotEmpty()

        val parsed = logcatResult.out.mapNotNull { parseLogLine(it, managerName) }
            // 按时间倒序（logcat 本身已经是旧→新，反转后新→旧）
            .reversed()

        // ===== 2. 已授权列表补充（logcat 里没有的授权记录） =====
        val knownGranted = AppSettingsRef.grantedApps
        val logcatPackages = parsed.map { it.packageName }.toSet()
        val extraGranted = knownGranted.filter { it !in logcatPackages }.map { pkg ->
            AuditEntry(
                timestamp = 0L,
                packageName = pkg,
                appName = pkg,
                uid = -1,
                granted = true,
                source = "已授权列表",
                rawLog = "来自 AppSettings.grantedApps"
            )
        }

        AuditReport(
            entries = (parsed + extraGranted).take(maxLines),
            managerName = managerName,
            totalRequests = parsed.size,
            dataSource = if (logcatAvailable) "logcat + 授权列表" else "授权列表（logcat 不可用）",
            logcatAvailable = logcatAvailable
        )
    }

    /**
     * 解析单行 logcat
     *
     * 支持的格式（宽松匹配）：
     *   - "granted su to uid 10123 (com.xxx.app)"
     *   - "su: request from uid 10123 (com.xxx.app)"
     *   - "ksu: su granted to com.xxx.app"
     *   - "magisk: com.xxx.app granted root"
     *   - "huijui: grant uid=10123 pkg=com.xxx.app"
     */
    private fun parseLogLine(line: String, managerName: String): AuditEntry? {
        if (line.isBlank()) return null

        // 抓包名（Android 包名格式：com.xxx.yyy）
        val pkgRegex = Regex("([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)")
        val pkgMatch = pkgRegex.find(line) ?: return null
        val packageName = pkgMatch.value

        // 抓 UID
        val uidRegex = Regex("uid[=: ]\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val uid = uidRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1

        // 判断是否授权（"grant" / "allow" / "deny" / "reject" 都是关键词）
        val lower = line.lowercase()
        val granted = !(lower.contains("deny") || lower.contains("reject") || lower.contains("refused"))

        // 抓时间（logcat 格式：MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: msg）
        val timeRegex = Regex("^(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})")
        val timestamp = timeRegex.find(line)?.let { m ->
            try {
                val month = m.groupValues[1].toInt()
                val day = m.groupValues[2].toInt()
                val hour = m.groupValues[3].toInt()
                val minute = m.groupValues[4].toInt()
                val second = m.groupValues[5].toInt()
                val millis = m.groupValues[6].toInt()
                // 用当前年份补全（logcat 不打印年份）
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.MONTH, month - 1)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                cal.set(java.util.Calendar.MINUTE, minute)
                cal.set(java.util.Calendar.SECOND, second)
                cal.set(java.util.Calendar.MILLISECOND, millis)
                cal.timeInMillis
            } catch (_: Exception) {
                0L
            }
        } ?: 0L

        return AuditEntry(
            timestamp = timestamp,
            packageName = packageName,
            appName = packageName,
            uid = uid,
            granted = granted,
            source = managerName,
            rawLog = line
        )
    }

    /**
     * 内部访问 AppSettings.grantedApps（避免循环依赖）
     */
    private object AppSettingsRef {
        val grantedApps: Set<String>
            get() = com.huijui.su.next.core.utils.AppSettings.grantedApps
    }
}
