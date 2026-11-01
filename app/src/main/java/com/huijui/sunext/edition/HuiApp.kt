package com.huijui.sunext.edition

import android.app.Application
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.CrashHandler
import com.huijui.sunext.edition.core.utils.SystemGuardian

/**
 * 进程级初始化。
 *
 * 为什么需要它：`AppSettings.prefs` 是 lateinit，而 `AppSettings.init()` 之前只在
 * MainActivity.onCreate 里调用。但 SystemMonitorService / RootAuditService 都是
 * START_STICKY —— 进程被杀之后系统可以**脱离 Activity** 直接重建服务，
 * 这时 prefs 还没初始化，任何 AppSettings 访问都会抛
 * UninitializedPropertyAccessException，表现为「后台莫名崩溃」。
 *
 * 放到 Application.onCreate 里，任何入口（Activity / Service / 未来的 Receiver）
 * 都保证先初始化。
 */
class HuiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        CrashHandler.init(this)
        SystemGuardian.init(this)
    }
}
