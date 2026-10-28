package com.huijui.su.next.core.utils

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
            
            val logFile = File(appContext.filesDir, "hui_crash.log")
            logFile.appendText(log)
            
            // 也可以尝试写入 /data/adb/huijuisu/crash.log
            File("/data/adb/huijuisu/crash.log").appendText(log)
        } catch (_: Exception) { }
        
        defaultHandler?.uncaughtException(t, e)
    }
}
