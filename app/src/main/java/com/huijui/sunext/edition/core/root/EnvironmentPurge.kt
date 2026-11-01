package com.huijui.sunext.edition.core.root

import android.content.Context
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HUI 环境自毁（"自爆"）。
 *
 * 边界必须说清楚：**它销毁的是 HUI 自己这一层环境，不是设备，也不是用户数据。**
 *   - 不动任何分区、不写 boot / init_boot / vbmeta
 *   - 不删用户文件
 *   - 不卸载应用
 * 它只做四件事：撤掉模块挂载、杀掉守护进程、清空 Root 授权、留下取证日志。
 *
 * 触发条件在 [CheatSentinel] 里：作弊工具在跑 + 联机游戏在跑 + 确实拿到了 Root，
 * 三条件同时成立才会走到这里。只装性能模块的用户不满足其中任何一条。
 */
object EnvironmentPurge {

    data class PurgeResult(
        val success: Boolean,
        val steps: List<String>,
        val logPath: String?
    )

    private const val LOCK_HOURS = 24L

    suspend fun execute(context: Context, report: CheatSentinel.Report): PurgeResult =
        withContext(Dispatchers.IO) {
            val steps = mutableListOf<String>()

            // 1. 撤掉内核模块的挂载（HUI 私有接口；不存在也不报错）
            val unmount = HuijuiShell.exec(
                "echo 'CLEAR_MOUNTS' > /proc/hui_config 2>/dev/null; echo __RC=\$?",
                timeoutMs = 4_000L
            )
            steps += if (unmount.isSuccess) "已请求内核撤除模块挂载" else "内核挂载接口不可用（可能本来就未挂载）"

            // 2. 杀掉守护进程：HUI 的 socket 通道随之消失
            val kill = HuijuiShell.exec(
                "killall huijuisud 2>/dev/null; pkill -f huijuisud 2>/dev/null; echo done",
                timeoutMs = 4_000L
            )
            steps += if (kill.out.joinToString("").contains("done")) "守护进程已终止" else "守护进程终止指令已下发"

            // 3. 清空全部 Root 授权
            val revoked = AppSettings.grantedApps.size
            AppSettings.grantedApps = emptySet()
            AppSettings.save()
            steps += "已撤销 $revoked 个应用的 Root 授权"

            // 4. 关掉所有隐藏/伪装能力（自爆之后没什么可藏的了）
            AppSettings.isHideRootEnabled = false
            AppSettings.isHideSelinuxEnabled = false
            AppSettings.isAntiRootLossEnabled = false
            AppSettings.save()
            steps += "隐藏与伪装能力已全部关闭"

            // 5. 上锁：24 小时内不接受重新启用
            val until = System.currentTimeMillis() + LOCK_HOURS * 3600_000L
            AppSettings.cheatLockUntil = until
            AppSettings.cheatPurgeCount = AppSettings.cheatPurgeCount + 1
            AppSettings.save()
            steps += "HUI 环境锁定至 ${formatTime(until)}"

            // 6. 取证日志
            val logPath = writeForensicLog(context, report, steps)
            if (logPath != null) steps += "取证日志：$logPath"

            PurgeResult(true, steps, logPath)
        }

    private suspend fun writeForensicLog(
        context: Context,
        report: CheatSentinel.Report,
        steps: List<String>
    ): String? = runCatching {
        val dir = File("/sdcard/Download/HuiJuiSU_Backup").apply { mkdirs() }
        val file = File(dir, "cheat_purge_${System.currentTimeMillis()}.log")
        val text = buildString {
            appendLine("HuiJuiSU 环境自毁记录")
            appendLine("时间: ${formatTime(System.currentTimeMillis())}")
            appendLine("判定: ${report.verdict} / 分数 ${report.score}")
            appendLine("目标: ${report.target ?: "未知"}")
            appendLine()
            appendLine("信号:")
            report.signals.forEach { appendLine("  [${it.id}] +${it.weight} ${it.detail}") }
            appendLine()
            appendLine("执行:")
            steps.forEach { appendLine("  · $it") }
        }
        // 直接用 shell 写，避免应用层存储权限问题
        val tmp = File(context.cacheDir, "purge.log")
        tmp.writeText(text)
        HuijuiShell.exec("cp '${tmp.absolutePath}' '${file.absolutePath}' 2>/dev/null; echo ok")
        tmp.delete()
        file.absolutePath
    }.getOrNull()

    fun isLocked(): Boolean = System.currentTimeMillis() < AppSettings.cheatLockUntil

    fun remainingLockText(): String {
        val left = AppSettings.cheatLockUntil - System.currentTimeMillis()
        if (left <= 0) return "未锁定"
        val hours = left / 3600_000L
        val minutes = (left % 3600_000L) / 60_000L
        return "剩余 $hours 小时 $minutes 分"
    }

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
}
