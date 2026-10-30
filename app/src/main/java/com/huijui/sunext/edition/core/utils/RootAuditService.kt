package com.huijui.sunext.edition.core.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.huijui.sunext.edition.MainActivity
import com.huijui.sunext.edition.R
import com.huijui.sunext.edition.core.root.RootAuditProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 📋 Root 审计前台服务（可选）
 *
 * 与 M1 系统监控服务独立：
 *   - M1 显示 CPU/温度
 *   - 本服务显示"最近一次 Root 请求"
 *
 * 默认关闭，用户主动开启才跑。低功耗设计：
 *   - 每 10 秒拉一次 logcat（比 M1 的 5 秒更省电）
 *   - 只在请求数变化时更新通知
 */
class RootAuditService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    @Volatile private var isMonitoring = false
    @Volatile private var lastCount = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(null, "监控已启动", 0))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        monitorJob = serviceScope.launch {
            while (isMonitoring) {
                try {
                    val report = RootAuditProvider.fetchRecentAudits(10)
                    if (report.totalRequests != lastCount) {
                        val latest = report.entries.firstOrNull()
                        val summary = when {
                            latest == null -> "暂无 Root 请求"
                            latest.granted -> "最近：${latest.packageName}（已授权）"
                            else -> "最近：${latest.packageName}（已拒绝）"
                        }
                        updateNotification(buildNotification(latest, summary, report.totalRequests))
                        lastCount = report.totalRequests
                    }
                } catch (_: Exception) {}
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        isMonitoring = false
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        latest: RootAuditProvider.AuditEntry?,
        summary: String,
        count: Int
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RootAuditService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hui_foreground)
            .setContentTitle("HuiJuiSU 审计 · 共 $count 条")
            .setContentText(summary)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun updateNotification(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Root 审计", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示最近一次 Root 请求"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "huijui_root_audit"
        private const val NOTIFICATION_ID = 1002
        private const val UPDATE_INTERVAL_MS = 10_000L
        const val ACTION_STOP = "com.huijui.sunext.edition.STOP_AUDIT"

        fun start(context: Context) {
            val intent = Intent(context, RootAuditService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RootAuditService::class.java))
        }
    }
}
