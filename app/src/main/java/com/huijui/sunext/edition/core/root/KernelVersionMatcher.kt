package com.huijui.sunext.edition.core.root

import android.os.Build

object KernelVersionMatcher {

    data class GkiInfo(val branch: String, val arch: String)

    // 获取当前运行环境的内核版本
    fun getCurrentGkiInfo(): GkiInfo? {
        val arch = System.getProperty("os.arch") ?: "aarch64"
        val targetArch = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x86_64"

        val kernelRelease = try {
            ProcessBuilder("uname", "-r").start().inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        return matchKmi(kernelRelease, targetArch)
    }

    // 根据传入的内核字符串匹配对应的 KMI
    fun matchKmi(kernelRelease: String, arch: String): GkiInfo? {
        if (kernelRelease.isBlank()) return null
        
        val androidVerMatch = Regex("android(\\d+)").find(kernelRelease)
        val kernelMajorMatch = Regex("^(\\d+\\.\\d+)").find(kernelRelease)

        val androidVer = androidVerMatch?.groupValues?.get(1) ?: Build.VERSION.RELEASE.split(".")[0]
        val kernelMajor = kernelMajorMatch?.groupValues?.get(1) ?: "5.10"

        val branch = when {
            androidVer == "12" -> "android12-5.10"
            androidVer == "13" -> if (kernelMajor == "5.15") "android13-5.15" else "android13-5.10"
            androidVer == "14" -> if (kernelMajor == "5.15") "android14-5.15" else "android14-6.1"
            androidVer == "15" -> "android15-6.6"
            androidVer == "16" -> "android16-6.12"
            androidVer == "17" -> "android17-6.18"
            else -> null
        } ?: return null

        return GkiInfo(branch, arch)
    }

    fun getExpectedKoFileName(): String? {
        val info = getCurrentGkiInfo() ?: return null
        return "HuiJuiPhantom_${info.branch}_${info.arch}.ko"
    }
}
