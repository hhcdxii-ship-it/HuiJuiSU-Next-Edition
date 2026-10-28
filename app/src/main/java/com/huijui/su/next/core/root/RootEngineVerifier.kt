package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootEngineVerifier {

    const val HUI_DEVICE = "/dev/huijui"

    data class VerifyReport(
        val isActive: Boolean,
        val devicePresent: Boolean,
        val moduleLoaded: Boolean,
        val channelRoot: Boolean,
        val detail: String
    )

    suspend fun verifyDetailed(): VerifyReport = withContext(Dispatchers.IO) {
        // 1. 检查字符设备 /dev/huijui 是否存在
        val devicePresent = runCatching {
            HuijuiShell.exec("test -c $HUI_DEVICE && echo yes || echo no")
                .out.joinToString("").trim() == "yes"
        }.getOrDefault(false)

        // 2. 检查内核里是否有 huijui 的模块
        val moduleLoaded = runCatching {
            HuijuiShell.exec("lsmod | grep huijui_lkm >/dev/null 2>&1 && echo yes || echo no")
                .out.joinToString("").trim() == "yes"
        }.getOrDefault(false)

        // 3. 检查 Shell 是否真的拿到了 Root（真提权）
        val channelRoot = runCatching { HuijuiShell.isRooted() }.getOrDefault(false)
        
        val active = devicePresent && channelRoot

        val detail = buildString {
            append("HUI设备=").append(if (devicePresent) "✅" else "❌")
            append(" 模块=").append(if (moduleLoaded) "✅" else "❌")
            append(" Root=").append(if (channelRoot) "✅" else "❌")
        }

        VerifyReport(active, devicePresent, moduleLoaded, channelRoot, detail)
    }

    suspend fun verifyHuijuiActive(): Boolean = verifyDetailed().isActive

    suspend fun checkOtherManagers(): String? = withContext(Dispatchers.IO) {
        when {
            runCatching { HuijuiShell.exec("ls /data/adb/magisk >/dev/null 2>&1 && echo y").out.joinToString("").contains("y") }.getOrDefault(false) -> "Magisk"
            runCatching { HuijuiShell.exec("ls /data/adb/ksu >/dev/null 2>&1 && echo y").out.joinToString("").contains("y") }.getOrDefault(false) -> "KernelSU"
            else -> null
        }
    }
}
