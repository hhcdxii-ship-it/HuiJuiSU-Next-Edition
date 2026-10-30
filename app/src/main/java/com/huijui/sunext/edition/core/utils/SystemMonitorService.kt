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
import com.huijui.sunext.edition.core.root.CpuInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 📊 系统监控前台服务
 *
 * 功能：
 *   - 常驻通知栏显示 CPU 温度 / 核心频率 / 性能模式
 *   - 每 5 秒更新一次数据
 *   - 低功耗设计：只在用户主动开启时运行，关闭后彻底停止
 *
 * 通知内容（单行紧凑）：
 *   🌡️ 42.5°C · ⚡ 2.4GHz · 性能模式
 *
 * 启动方式：
 *   SystemMonitorService.start(context)
 *
 * 停止方式：
 *   SystemMonitorService.stop(context)  // 或点击通知栏停止按钮
 */
class SystemMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    @Volatile private var isMonitoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 🎯 处理停止 action（来自通知栏「停止监控」按钮）
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // 🎯 立即用占位通知启动前台（Android 8+ 要求 5 秒内 startForeground）
        startForeground(NOTIFICATION_ID, buildNotification(0f, 0L, false, "初始化中…"))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        monitorJob = serviceScope.launch {
            while (isMonitoring) {
                try {
                    val status = CpuInfoProvider.getStatus()
                    val avgFreqMhz = if (status.cores.isNotEmpty()) {
                        status.cores.map { it.curFreq }.average().toLong() / 1000
                    } else 0L

                    val notification = buildNotification(
                        temperature = status.temperature,
                        avgFreqMhz = avgFreqMhz,
                        performanceMode = status.performanceMode,
                        governor = status.currentGovernor
                    )
                    updateNotification(notification)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    // ==========================================
    // 通知构建
    // ==========================================

    private fun buildNotification(
        temperature: Float,
        avgFreqMhz: Long,
        performanceMode: Boolean,
        governor: String
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
            Intent(this, SystemMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = buildString {
            if (temperature > 0) append("🌡️ ${"%.1f".format(temperature)}°C")
            else append("🌡️ —")
            if (avgFreqMhz > 0) {
                if (isNotEmpty()) append(" · ")
                append("⚡ ${"%.2f".format(avgFreqMhz / 1000.0f)}GHz")
            }
            if (performanceMode) {
                if (isNotEmpty()) append(" · ")
                append("🚀 性能模式")
            } else if (governor.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(governor)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hui_foreground)
            .setContentTitle("HuiJuiSU 监控")
            .setContentText(statusText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "停止监控", stopIntent)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "系统监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 CPU/内存/温度实时状态"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "huijui_system_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 5_000L
        const val ACTION_STOP = "com.huijui.sunext.edition.STOP_MONITOR"

        fun start(context: Context) {
            val intent = Intent(context, SystemMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SystemMonitorService::class.java))
        }
    }
}
