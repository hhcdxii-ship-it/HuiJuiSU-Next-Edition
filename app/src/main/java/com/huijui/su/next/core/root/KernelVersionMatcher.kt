package com.huijui.su.next.core.root

import android.os.Build

object KernelVersionMatcher {

    data class GkiInfo(val branch: String, val arch: String)

    fun getCurrentGkiInfo(): GkiInfo? {
        val arch = System.getProperty("os.arch") ?: "aarch64"
        val targetArch = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x86_64"

        // 获取内核版本 (如 6.1.25-android14-11-g...)
        val kernelRelease = try {
            ProcessBuilder("uname", "-r").start().inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        val androidVerMatch = Regex("android(\\d+)").find(kernelRelease)
        val kernelMajorMatch = Regex("^(\\d+\\.\\d+)").find(kernelRelease)

        // 如果由于某些原因 uname 没拿到 android 版本，用 Build.VERSION 兜底
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

        return GkiInfo(branch, targetArch)
    }

    // 🚀 生成最终应该加载的文件名，并加入智能容错
    fun getExpectedKoFileName(): String? {
        val info = getCurrentGkiInfo() ?: return null
        return "HUI_${info.branch}_${info.arch}.ko"
    }
}
