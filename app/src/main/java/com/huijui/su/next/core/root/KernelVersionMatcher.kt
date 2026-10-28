package com.huijui.su.next.core.root

/**
 * 🩸 纯血内核版本嗅探器
 * 负责识别当前手机的内核，并匹配 Assets 中对应的 HUI 模块
 */
object KernelVersionMatcher {

    data class GkiInfo(val branch: String, val arch: String)

    // 获取当前内核信息并解析
    fun getCurrentGkiInfo(): GkiInfo? {
        // 1. 获取架构
        val arch = System.getProperty("os.arch") ?: return null
        val targetArch = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x86_64"

        // 2. 获取 uname -r 的结果
        val kernelRelease = try {
            val process = ProcessBuilder("uname", "-r").start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { return null }

        // 3. 用正则提取 Android 大版本和内核主版本
        // 例如：6.1.25-android14-11-g... 提取出 14 和 6.1
        val androidVerMatch = Regex("android(\\d+)").find(kernelRelease)
        val kernelMajorMatch = Regex("^(\\d+\\.\\d+)").find(kernelRelease)

        val androidVer = androidVerMatch?.groupValues?.get(1) ?: return null
        val kernelMajor = kernelMajorMatch?.groupValues?.get(1) ?: "5.10" // 兜底

        // 4. 映射到 GKI 分支名
        // 因为同一个 Android 版本可能有多个内核版本（如 Android 13 有 5.10 和 5.15）
        val branch = when {
            androidVer == "12" -> "android12-5.10"
            androidVer == "13" -> if (kernelMajor == "5.15") "android13-5.15" else "android13-5.10"
            androidVer == "14" -> if (kernelMajor == "5.15") "android14-5.15" else "android14-6.1"
            androidVer == "15" -> "android15-6.6"
            androidVer == "16" -> "android16-6.12"
            androidVer == "17" -> "android17-6.18"
            else -> return null // 不支持的系统
        }

        return GkiInfo(branch, targetArch)
    }

    // 生成最终应该加载的 Assets 文件名
    fun getExpectedKoFileName(): String? {
        val info = getCurrentGkiInfo() ?: return null
        return "huijui_lkm_${info.branch}_${info.arch}.ko"
    }
}
