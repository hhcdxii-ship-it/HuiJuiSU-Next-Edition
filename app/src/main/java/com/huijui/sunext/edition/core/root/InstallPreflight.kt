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
        /** 当前是否已拿到 Root —— 没有的话分区项只能显示"未验证" */
        val hasRoot: Boolean = false,
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
            appendLine(
                "目标分区: " + when {
                    partitionName != null -> partitionName + slot
                    !hasRoot -> "未验证（需要 Root 授权）"
                    else -> "未找到"
                }
            )
            appendLine("设备节点: " + (partitionDevice ?: "未找到"))
        }
    }

    /** 找到 by-name 目录的真实位置；找不到返回空串。 */
    private suspend fun resolveByNameDir(): String = runCatching {
        val direct = HuijuiShell.exec("test -d /dev/block/by-name && echo yes")
        if (direct.out.joinToString("").contains("yes")) return "/dev/block/by-name"
        HuijuiShell.exec("ls -d /dev/block/platform/*/by-name 2>/dev/null | head -n 1")
            .out.firstOrNull()?.trim().orEmpty()
    }.getOrDefault("")

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val androidRelease = Build.VERSION.RELEASE ?: "?"
        val api = Build.VERSION.SDK_INT
        val release = KernelVersionMatcher.currentKernelRelease()

        // /dev/block/by-name 需要 Root 才能读。
        // 引导页阶段用户往往还没授权，此时所有分区探测都会返回"不存在"，
        // 于是被误判成「找不到 boot 分区」——这是体检误报的根源。
        // 没 Root 时分区只能报「未验证」，绝不能据此阻止流程。
        val hasRoot = runCatching { HuijuiShell.isRooted() }.getOrDefault(false)

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
        //
        // ⚠️ 不能只认 /dev/block/by-name：不少机型（尤其高通/联发科老平台）的分区
        //    链接在 /dev/block/platform/<控制器>/by-name 下面，写死单一路径会
        //    误报「找不到 init_boot_a 分区」，把一个能正常刷的机器判死。
        val byNameDir = if (hasRoot) resolveByNameDir() else ""
        fun cand(name: String): List<String> = buildList {
            if (byNameDir.isNotBlank()) add("$byNameDir/$name")
            add("/dev/block/by-name/$name")
        }

        val hasInitBoot = hasRoot && (
            cand("init_boot$slot").any { InstallIo.exists(it) } ||
                cand("init_boot").any { InstallIo.exists(it) }
            )
        val partitionName = if (hasInitBoot) "init_boot" else "boot"

        // 先试带槽位、再试不带槽位
        val devicePath = if (!hasRoot) {
            null
        } else {
            (cand(partitionName + slot) + cand(partitionName)).firstOrNull { InstallIo.exists(it) }
        }

        // vendor_boot 提醒：部分 GKI 2.0 机型的 ramdisk 在 vendor_boot 里，
        // 这种机型刷 boot/init_boot 不会生效。
        val hasVendorBoot = InstallIo.exists("/dev/block/by-name/vendor_boot$slot")

        val blocking = koBlocking ?: when {
            // 有 Root 却真的找不到分区 -> 这才是硬错误
            hasRoot && devicePath == null ->
                "找不到 $partitionName 的设备节点（已尝试 by-name 与 platform/by-name，槽位=\"$slot\"）。" +
                    "可能是分区名不同"
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
            hasRoot = hasRoot,
            blocking = blocking
        )
    }
}
