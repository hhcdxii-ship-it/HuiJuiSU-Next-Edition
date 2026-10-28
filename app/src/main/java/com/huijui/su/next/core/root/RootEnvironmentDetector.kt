package com.huijui.su.next.core.root

import android.content.Context

enum class RootEnvironment(val displayName: String) {
    KERNELSU("KernelSU"),
    KERNELSU_NEXT("KernelSU Next"),
    APATCH("APatch"),
    MAGISK("Magisk"),
    MAGISK_KITSUNE("Magisk Kitsune"),
    OTHER("未知环境")
}

object RootEnvironmentDetector {
    fun detect(): RootEnvironment {
        if (HuijuiShell.exec("ls /data/adb/apd").isSuccess) {
            return RootEnvironment.APATCH
        }
        if (HuijuiShell.exec("ls /data/adb/ksud").isSuccess) {
            if (HuijuiShell.exec("ls /data/adb/ksu/next").isSuccess) {
                return RootEnvironment.KERNELSU_NEXT
            }
            return RootEnvironment.KERNELSU
        }
        if (HuijuiShell.exec("ls /data/adb/magisk").isSuccess || HuijuiShell.exec("which magisk").isSuccess) {
            if (HuijuiShell.exec("ls /data/adb/magisk/kitsune").isSuccess) {
                return RootEnvironment.MAGISK_KITSUNE
            }
            return RootEnvironment.MAGISK
        }
        return RootEnvironment.OTHER
    }

    /**
     * 🚀 检测是否为 Built-in 模式
     */
    fun isBuiltInMode(): Boolean {
        val kernelInfo = HuijuiShell.exec("cat /proc/version").out.joinToString(" ")
        val isLkmRunning = HuijuiShell.exec("lsmod | grep kernelsu").isSuccess
        return kernelInfo.contains("KernelSU", ignoreCase = true) && !isLkmRunning
    }

    /**
     * 🚀 检测是否为越狱模式 (SELinux Permissive)
     */
    fun isJailbreakMode(): Boolean {
        val selinux = HuijuiShell.exec("getenforce").out.firstOrNull()?.trim()
        return selinux == "Permissive"
    }

    fun switchToHuijuiSU(context: Context, currentEnv: RootEnvironment): Boolean {
        val packageName = context.packageName
        return when (currentEnv) {
            RootEnvironment.MAGISK -> {
                val sql = "UPDATE policies SET package_name='$packageName' WHERE package_name='com.topjohnwu.magisk';"
                HuijuiShell.exec("magisk --sqlite \"$sql\"").isSuccess
            }
            RootEnvironment.MAGISK_KITSUNE -> {
                val sql = "UPDATE policies SET package_name='$packageName' WHERE package_name='io.github.huskydg.magisk';"
                HuijuiShell.exec("magisk --sqlite \"$sql\"").isSuccess
            }
            RootEnvironment.KERNELSU, RootEnvironment.KERNELSU_NEXT -> {
                HuijuiShell.exec("echo '$packageName' > /data/adb/ksu/package_name").isSuccess
            }
            RootEnvironment.APATCH -> {
                HuijuiShell.exec("echo '$packageName' > /data/adb/ap/package_name").isSuccess
            }
            else -> false
        }
    }
}
