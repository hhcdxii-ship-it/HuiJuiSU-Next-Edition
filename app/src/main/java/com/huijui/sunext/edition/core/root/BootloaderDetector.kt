package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BootloaderDetector {

    data class BootState(
        val isBootloaderLocked: Boolean,
        val isProbablyRootedEnv: Boolean,
        val isSuspicious: Boolean
    )

    suspend fun detectBootState(): BootState = withContext(Dispatchers.IO) {
        var locked = true
        var hasRootEnv = false
        var suspicious = false

        try {
            // 1. 尝试读取属性 (可能会被 resetprop 等 Hook)
            val flashLocked = readProp("ro.boot.flash.locked")
            val verifiedState = readProp("ro.boot.verifiedbootstate")
            val vbmetaState = readProp("ro.boot.vbmeta.device_state")

            if (flashLocked == "0" || verifiedState == "orange" || vbmetaState == "unlocked") {
                locked = false
            }

            // 2. 交叉验证底层环境：纯文件API探测，防 getprop Hook
            // 只要文件系统里有 /data/adb/ksu 等目录，说明绝对解锁过并刷了 Root
            hasRootEnv = File("/data/adb/ksu").exists() || 
                         File("/data/adb/magisk").exists() || 
                         File("/data/adb/ap").exists() ||
                         File("/system/bin/su").exists() ||
                         File("/system/xbin/su").exists()

            // 3. 逻辑矛盾判定：属性说锁了，但底层有 Root 目录（典型的 伪回锁）
            if (locked && hasRootEnv) {
                suspicious = true
            }

            // 4. 属性自身的逻辑矛盾
            if (flashLocked == "0" && verifiedState == "green") {
                suspicious = true
            }

        } catch (e: Exception) {
            // 异常忽略
        }

        BootState(locked, hasRootEnv, suspicious)
    }

    private fun readProp(prop: String): String {
        return try {
            val p = Runtime.getRuntime().exec("getprop $prop")
            p.inputStream.bufferedReader().readText().trim().also { p.waitFor() }
        } catch (e: Exception) { "" }
    }
}
