package com.huijui.sunext.edition.core.root

import android.os.Build

/**
 * 内核 KMI 匹配。
 *
 * 重构前的问题（会直接导致"刷完没效果"甚至内核 panic）：
 *
 *   ```kotlin
 *   androidVer == "14" -> if (kernelMajor == "5.15") "android14-5.15" else "android14-6.1"
 *   ```
 *   一个 6.6 内核的 Android 14 设备（真机存在，比如部分 GKI 2.0 机型）会被**错配成 6.1 的模块**。
 *   版本魔数对不上，`insmod` 必然失败；而 phantom 类模块常用 kallsyms 手法，
 *   在内核结构体布局不同的情况下强行加载有概率直接 panic。
 *
 *   同理 `androidVer == "15" -> "android15-6.6"` 把版本号也写死了。
 *
 * 现在改成一张精确映射表：**版本对不上就返回 null**，由调用方给出明确错误，
 * 绝不"猜一个最像的"去 insmod。
 */
object KernelVersionMatcher {

    data class GkiInfo(val branch: String, val arch: String)

    private data class Kmi(val androidMajor: Int, val kernelMajor: String, val branch: String)

    /** 与 `assets/exploit/` 里的 8 个模块严格一一对应。 */
    private val KMI_TABLE: List<Kmi> = listOf(
        Kmi(12, "5.10", "android12-5.10"),
        Kmi(13, "5.10", "android13-5.10"),
        Kmi(13, "5.15", "android13-5.15"),
        Kmi(14, "5.15", "android14-5.15"),
        Kmi(14, "6.1", "android14-6.1"),
        Kmi(15, "6.6", "android15-6.6"),
        Kmi(16, "6.12", "android16-6.12"),
        Kmi(17, "6.18", "android17-6.18"),
    )

    fun supportedBranches(): List<String> = KMI_TABLE.map { it.branch }

    /** 当前运行内核的 KMI。 */
    fun getCurrentGkiInfo(): GkiInfo? = matchKmi(currentKernelRelease(), "arm64")

    fun currentKernelRelease(): String {
        val byProperty = System.getProperty("os.version")?.trim().orEmpty()
        if (byProperty.isNotBlank()) return byProperty
        return runCatching {
            ProcessBuilder("uname", "-r").start().inputStream.bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
    }

    /**
     * @param kernelRelease 形如 `5.10.198-android13-8-00001-g...`
     */
    fun matchKmi(kernelRelease: String, arch: String): GkiInfo? {
        if (kernelRelease.isBlank()) return null

        val kernelMajor = Regex("""^(\d+\.\d+)""").find(kernelRelease.trim())
            ?.groupValues?.get(1)
            ?: return null

        // 内核串里的 android13 比用户态 Android 版本更可信：它描述的是这批 GKI 的构建目标
        val androidMajor = Regex("""android\s*(\d+)""", RegexOption.IGNORE_CASE).find(kernelRelease)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: Build.VERSION.RELEASE.substringBefore('.').toIntOrNull()
            ?: return null

        val branch = KMI_TABLE.firstOrNull {
            it.androidMajor == androidMajor && it.kernelMajor == kernelMajor
        }?.branch ?: return null

        return GkiInfo(branch, arch)
    }

    fun getExpectedKoFileName(): String? {
        val info = getCurrentGkiInfo() ?: return null
        return "HuiJuiPhantom_${info.branch}_${info.arch}.ko"
    }

    /** 给用户看的诊断串，报错时直接拼进去。 */
    fun describe(): String {
        val release = currentKernelRelease()
        val info = matchKmi(release, "arm64")
        return if (info != null) {
            "内核 $release → ${info.branch}"
        } else {
            "内核 $release → 无匹配（内置仅支持 ${supportedBranches().joinToString("、")}）"
        }
    }
}
