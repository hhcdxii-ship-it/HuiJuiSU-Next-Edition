package com.huijui.sunext.edition.core.utils

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashHandler : Thread.UncaughtExceptionHandler {
    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var installed = false

    fun init(context: Context) {
        // 必须幂等：Application 和 Activity 都会调，第二次进来时
        // Thread.getDefaultUncaughtExceptionHandler() 拿到的就是 CrashHandler 自己，
        // 一旦发生异常就会无限递归。
        if (installed) return
        installed = true
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val log = "=== HUI Crash at $timestamp ===\n${sw}\n"
            
            // 应用私有目录一定可写，这是主日志
            runCatching {
                File(appContext.filesDir, "hui_crash.log").appendText(log)
            }

            // /data/adb 只有 root 能写，普通进程必然失败；
            // 单独包一层 runCatching，避免它把上面的主日志一起带崩
            runCatching {
                File("/data/adb/huijuisu/crash.log").appendText(log)
            }
        } catch (_: Exception) { }
        
        defaultHandler?.uncaughtException(t, e)
    }
}
