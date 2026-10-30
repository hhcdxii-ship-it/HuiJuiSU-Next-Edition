package com.huijui.sunext.edition.core.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SystemMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    @Volatile private var isMonitoring = false

    // 🚀 核心功耗优化：感知屏幕状态
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // 灭屏时立刻停止刷新，释放 CPU
                    stopMonitoring()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // 亮屏时恢复刷新
                    startMonitoring()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 动态注册屏幕广播
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(0f, 0L, false, "初始化中…"))
        
        // 检查电量，低电量直接警告
        val battery = getBatteryLevel()
        if (battery in 0..15) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(0f, 0L, false, "电量过低，已停止监控"))
            stopMonitoring()
            return START_NOT_STICKY
        }

        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        monitorJob = serviceScope.launch {
            while (isActive && isMonitoring) {
                // 每次循环前，如果此时处于后台或灭屏，直接等
                // 广播已经接管了暂停逻辑，这里只要保证不重复就行
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
                // 🚀 稍微拉长一下间隔，5秒会做很多无用功，8秒足够
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
        // 🚀 闭环清理：强行撤掉通知，不留幽灵
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun buildNotification(
        temperature: Float,
        avgFreqMhz: Long,
        performanceMode: Boolean,
        governor: String
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SystemMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = buildString {
            if (temperature > 0) append("🌡️ ${"%.1f".format(temperature)}°C") else append("🌡️ —")
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
                CHANNEL_ID, "系统监控", NotificationManager.IMPORTANCE_LOW
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
        // 🚀 从 5秒 改为 8秒，降低唤醒频率
        private const val UPDATE_INTERVAL_MS = 8_000L 
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
