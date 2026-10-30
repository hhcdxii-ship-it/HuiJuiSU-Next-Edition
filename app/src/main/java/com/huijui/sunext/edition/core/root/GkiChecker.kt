package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🛡️ GKI / KMI 内核版本检查器
 *
 * 目的：
 *   刷 boot/init_boot 前，确认镜像里的 kernel 版本与当前系统一致，
 *   防止把 Android 13 的 boot 刷进 Android 14 设备（开机黑屏、无法启动）。
 *
 * 提取策略（按优先级）：
 *   1. huijui_boot kernel_version <img>（工具支持时，最准）
 *   2. strings <img> | grep "Linux version"（通用 fallback）
 *   3. 都失败 → canCheck=false，不阻断刷入，只给"无法检查"警告
 *
 * 比对策略：
 *   - 只比对"主版本.次版本"，如 6.1 vs 6.1（不管后面的 -android14-11-g...）
 *   - 不匹配 → 给警告，用户可强制刷（advancedMode）
 */
object GkiChecker {

    data class KernelCheckResult(
        val canCheck: Boolean,
        val matched: Boolean,
        val systemKernel: String,
        val imageKernel: String,
        val message: String
    )

    /**
     * 检查镜像里的 kernel 版本
     *
     * @param imagePath 镜像文件路径（boot/init_boot）
     * @return 检查结果
     */
    suspend fun checkImage(imagePath: String): KernelCheckResult = withContext(Dispatchers.IO) {
        // 1. 拿当前系统 kernel
        val unameRes = HuijuiShell.exec("uname -r")
        val systemKernel = unameRes.out.firstOrNull()?.trim() ?: ""
        if (systemKernel.isBlank()) {
            return@withContext KernelCheckResult(
                canCheck = false,
                matched = true,
                systemKernel = "",
                imageKernel = "",
                message = "无法读取系统内核版本，跳过检查"
            )
        }

        // 2. 从镜像提取 kernel 版本
        val imageKernel = extractImageKernel(imagePath)
        if (imageKernel.isBlank()) {
            return@withContext KernelCheckResult(
                canCheck = false,
                matched = true,
                systemKernel = systemKernel,
                imageKernel = "",
                message = "无法从镜像提取 kernel 版本，跳过检查（可能是非标准 boot.img）"
            )
        }

        // 3. 比对主版本号（6.1.75-android14-11-g... → 6.1）
        val sysShort = shortVersion(systemKernel)
        val imgShort = shortVersion(imageKernel)
        val matched = sysShort == imgShort

        KernelCheckResult(
            canCheck = true,
            matched = matched,
            systemKernel = systemKernel,
            imageKernel = imageKernel,
            message = if (matched) {
                "✅ 内核版本一致：$sysShort"
            } else {
                "⚠️ 内核版本不匹配：系统 $sysShort vs 镜像 $imgShort"
            }
        )
    }

    /**
     * 从 "6.1.75-android14-11-gxxxx" 提取 "6.1"
     */
    private fun shortVersion(version: String): String {
        val clean = version.trim().split("-").firstOrNull() ?: ""
        val parts = clean.split(".")
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else clean
    }

    /**
     * 从镜像文件提取 kernel 版本
     *
     * 尝试顺序：
     *   1. huijui_boot kernel_version <img>
     *   2. strings <img> | grep "Linux version"
     *   3. 都失败返回空字符串
     */
    private suspend fun extractImageKernel(imagePath: String): String {
        // 尝试 1：huijui_boot 子命令
        val r1 = HuijuiShell.exec(
            "huijui_boot kernel_version '$imagePath' 2>/dev/null",
            timeoutMs = 10_000L
        )
        if (r1.isSuccess) {
            val v = r1.out.firstOrNull()?.trim() ?: ""
            if (v.isNotBlank() && v.count { it == '.' } >= 1) {
                return v
            }
        }

        // 尝试 2：strings + grep
        val r2 = HuijuiShell.exec(
            "strings '$imagePath' 2>/dev/null | grep -m1 'Linux version'",
            timeoutMs = 15_000L
        )
        if (r2.isSuccess) {
            val line = r2.out.firstOrNull() ?: ""
            // 格式："Linux version 6.1.75-android14-11-g..."
            val match = Regex("Linux version (\\S+)").find(line)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return ""
    }
}
