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

    fun init(context: Context) {
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
