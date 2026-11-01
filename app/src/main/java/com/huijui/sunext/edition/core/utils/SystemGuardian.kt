package com.huijui.sunext.edition.core.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.huijui.sunext.edition.R
import com.huijui.sunext.edition.core.root.HuijuiShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 系统守护。
 *
 * 两个开关：
 *  - 防开发者选项被重置：周期性把 development_settings_enabled 写回 1
 *  - Root 掉线守护：Root 没了要告诉用户
 *
 * ⚠️ 其中「Root 掉线守护」的原实现是个**空 if**：
 * ```kotlin
 * if (!isRooted) {
 *     // 触发 Root 丢失提示逻辑
 * }
 * ```
 * 注释写了个待办就没了 —— 开关能打开、能持久化、但什么都不会发生。
 * 现在补成真的发通知，并且恢复时再提示一次。
 */
object SystemGuardian {

    private const val CHANNEL_ID = "huijui_guardian"
    private const val NOTIFICATION_ID = 1003

    private var isGuardianRunning = false
    private var guardianJob: Job? = null
    private var appContext: Context? = null

    /** Root 掉线的状态保持，避免每次循环都重复通知 */
    private var rootLossNotified = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun startGuardian(scope: CoroutineScope) {
        if (isGuardianRunning) return
        isGuardianRunning = true

        guardianJob = scope.launch(Dispatchers.IO) {
            try {
                while (isGuardianRunning) {
                    // 两个开关都关着就直接长睡，不做任何 shell 调用
                    if (!AppSettings.isAntiDevOptionsEnabled && !AppSettings.isAntiRootLossEnabled) {
                        delay(60_000)
                        continue
                    }

                    if (AppSettings.isAntiDevOptionsEnabled) {
                        // 针对 HyperOS OS1/OS2/OS3/OS4，强制重写开关
                        HuijuiShell.exec("settings put global development_settings_enabled 1")
                        HuijuiShell.exec("settings put global adb_enabled 1")

                        if (AppSettings.isXiaomiDevice) {
                            HuijuiShell.exec("settings put secure miui_optimization 0")
                        }
                    }

                    if (AppSettings.isAntiRootLossEnabled) {
                        val rooted = HuijuiShell.isRooted()
                        if (!rooted && !rootLossNotified) {
                            rootLossNotified = true
                            notify("Root 已掉线", "HUI 检测不到 Root 了，模块与守护功能可能已失效")
                        } else if (rooted && rootLossNotified) {
                            rootLossNotified = false
                            notify("Root 已恢复", "HUI 重新接通了内核通道")
                        }
                    }

                    // 10 秒一次 shell 太费电；系统设置被重置不是秒级事件，30 秒足够
                    delay(30_000)
                }
            } finally {
                // 保证标志位复位：否则一旦 job 被外部取消，
                // isGuardianRunning 会一直停留在 true，之后再也启动不了
                isGuardianRunning = false
            }
        }
    }

    fun stopGuardian() {
        isGuardianRunning = false
        guardianJob?.cancel()
        guardianJob = null
    }

    private fun notify(title: String, text: String) {
        val ctx = appContext ?: return
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "系统守护", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Root 掉线 / 恢复提醒" }
                )
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hui_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        }
    }
}
