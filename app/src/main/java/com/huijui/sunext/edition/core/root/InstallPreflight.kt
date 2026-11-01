package com.huijui.sunext.edition.core.root

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 刷入前置检测。
 *
 * 刷机这件事，**错一步就是砖**。之前 DirectInstaller 只判断了 init_boot 存不存在，
 * 其余全凭运气：
 *   - 内核不支持（不在 8 档 KMI 里）→ 照样往下走，最后卡在 insmod
 *   - 内置模块缺失 → 到写盘那一步才发现
 *   - vermagic 不匹配 → 写进去了但加载不了，用户以为"刷成功了"
 *   - 分区不存在 → dd 写到一个空设备节点上
 *
 * 这里把「Android 版本 / 内核版本 / KMI 匹配 / 目标分区 / 内置模块 / vermagic」
 * 一次性查清楚，任何一项不过就 blocking，绝不带着问题往下刷。
 */
object InstallPreflight {

    data class Result(
        val androidRelease: String,
        val apiLevel: Int,
        val kernelRelease: String,
        /** null = 当前内核不在支持的 8 档 KMI 内 */
        val kmiBranch: String?,
        val koAsset: String?,
        val koVermagic: String?,
        val koCompatible: Boolean,
        /** init_boot / boot / vendor_boot */
        val partitionName: String?,
        val partitionDevice: String?,
        val slot: String,
        /** 非 null = 不能继续，内容即为原因 */
        val blocking: String?
    ) {
        val canProceed: Boolean get() = blocking == null

        fun summary(): String = buildString {
            appendLine("Android: $androidRelease (API $apiLevel)")
            appendLine("内核: $kernelRelease")
            appendLine("KMI: " + (kmiBranch ?: "不支持"))
            appendLine("模块: " + (koAsset ?: "缺失"))
            appendLine("vermagic: " + (koVermagic ?: "未知") + if (koCompatible) "（匹配）" else "（不匹配）")
            appendLine("目标分区: " + (partitionName ?: "未找到") + slot)
            appendLine("设备节点: " + (partitionDevice ?: "未找到"))
        }
    }

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val androidRelease = Build.VERSION.RELEASE ?: "?"
        val api = Build.VERSION.SDK_INT
        val release = KernelVersionMatcher.currentKernelRelease()

        val kmi = KernelVersionMatcher.matchKmi(release, "arm64")
        val branch = kmi?.branch

        val slot = runCatching {
            HuijuiShell.exec("getprop ro.boot.slot_suffix").out.firstOrNull()?.trim().orEmpty()
        }.getOrDefault("")

        // ── 模块 ──
        var koAsset: String? = null
        var koVermagic: String? = null
        var compatible = false
        var koBlocking: String? = null

        if (branch == null) {
            koBlocking = "当前内核「$release」不在内置支持的 KMI 范围内（仅 " +
                KernelVersionMatcher.supportedBranches().joinToString("、") + "），无法刷入"
        } else {
            when (val pick = InstallIo.resolveKo(context, release)) {
                is InstallIo.KoPick.Ok -> {
                    koAsset = pick.assetName
                    koVermagic = pick.vermagic
                    compatible = pick.compatible
                }
                is InstallIo.KoPick.Missing -> koBlocking = pick.reason
            }
        }

        // ── 分区 ──
        // Android 13 起 ramdisk 从 boot 移到 init_boot（GKI 2.0）。
        // 有 init_boot 就只动 init_boot，绝不碰 boot；没有才回退 boot。
        val hasInitBoot = InstallIo.exists("/dev/block/by-name/init_boot$slot")
        val partitionName = if (hasInitBoot) "init_boot" else "boot"
        val devicePath = listOf(
            "/dev/block/by-name/$partitionName$slot",
            "/dev/block/by-name/$partitionName"
        ).firstOrNull { InstallIo.exists(it) }

        // vendor_boot 提醒：部分 GKI 2.0 机型的 ramdisk 在 vendor_boot 里，
        // 这种机型刷 boot/init_boot 不会生效。
        val hasVendorBoot = InstallIo.exists("/dev/block/by-name/vendor_boot$slot")

        val blocking = koBlocking ?: when {
            devicePath == null ->
                "找不到 $partitionName 的设备节点，可能分区名不同或没有 Root 权限"
            api >= 33 && !hasInitBoot && hasVendorBoot ->
                "系统为 Android $androidRelease，但没有 init_boot 分区而存在 vendor_boot。" +
                    "这类 GKI 2.0 机型的 ramdisk 位于 vendor_boot，刷 boot 不会生效，已中止以免刷坏。"
            !compatible ->
                "内置模块的 vermagic「${koVermagic ?: "未知"}」与当前内核「$release」不一致。" +
                    "刷入后极可能加载失败，已中止。"
            else -> null
        }

        Result(
            androidRelease = androidRelease,
            apiLevel = api,
            kernelRelease = release,
            kmiBranch = branch,
            koAsset = koAsset,
            koVermagic = koVermagic,
            koCompatible = compatible,
            partitionName = if (devicePath != null) partitionName else null,
            partitionDevice = devicePath,
            slot = slot,
            blocking = blocking
        )
    }
}
