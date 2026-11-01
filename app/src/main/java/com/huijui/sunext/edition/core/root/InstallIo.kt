package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 刷写 / 注入链路共用的底层工具。
 *
 * 为什么要单独抽一层：
 *   改造前 `DirectInstaller` / `OtaInstaller` / `HuijuiFlashManager` 各自实现了一份
 *   `ProcessBuilder("su","-c",...).start()`（直装里 20+ 次、OTA 里 20+ 次），
 *   每一条命令都新起一个 su 进程，慢、且**丢掉退出码** —— 只能靠 grep 报错文本判断成败。
 *   现在统一走 `HuijuiShell`（持久 su 会话），每条命令都能拿到真实 exit code。
 *
 * 这里不做任何业务判断，只提供可靠的原语。
 */
object InstallIo {

    data class Result(val ok: Boolean, val output: String)

    private const val DEFAULT_TIMEOUT = 120_000L
    private const val WRITE_TIMEOUT = 10 * 60_000L

    // ── 基础执行 ────────────────────────────────────────────────────

    suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT): Result {
        val r = HuijuiShell.exec(command, useCache = false, timeoutMs = timeoutMs)
        return Result(r.isSuccess, (r.out + r.err).joinToString("\n").trim())
    }

    suspend fun exists(path: String): Boolean =
        run("test -e '$path' && echo Y || echo N").output.contains("Y")

    suspend fun size(path: String): Long = firstLong(
        run("stat -c %s '$path' 2>/dev/null || echo 0")
    )

    /** 分区真实字节数。老 toybox 没有 blockdev 时退回 /sys 读扇区数。 */
    suspend fun partitionSize(device: String): Long {
        val byBlockdev = firstLong(run("blockdev --getsize64 '$device' 2>/dev/null || echo 0"))
        if (byBlockdev > 0L) return byBlockdev
        val node = run("readlink -f '$device' 2>/dev/null || echo '$device'").output.lineSequence()
            .firstOrNull()?.substringAfterLast('/').orEmpty()
        if (node.isBlank()) return 0L
        val sectors = firstLong(run("cat /sys/class/block/$node/size 2>/dev/null || echo 0"))
        return sectors * 512L
    }

    suspend fun currentSlot(): String = run("getprop ro.boot.slot_suffix").output
        .lineSequence().firstOrNull()?.trim().orEmpty()

    /** A/B 设备的另一槽；非 A/B 返回 null。 */
    suspend fun otherSlot(): String? = when (val s = currentSlot()) {
        "_a" -> "_b"
        "_b" -> "_a"
        else -> null
    }

    /** 在 by-name 下按顺序找第一个存在的分区，返回设备路径。 */
    suspend fun pickPartition(names: List<String>): String? {
        for (name in names) {
            val path = "/dev/block/by-name/$name"
            if (exists(path)) return path
        }
        return null
    }

    private fun firstLong(r: Result): Long =
        r.output.lineSequence().mapNotNull { it.trim().toLongOrNull() }.firstOrNull() ?: 0L

    // ── 写入与校验 ──────────────────────────────────────────────────

    /**
     * 把镜像写进分区。
     *
     * 相对旧代码补的四件事：
     *   1. `conv=fsync` + 末尾 `sync` —— 旧代码写完直接提示"成功了快去重启"，
     *      数据还在 page cache 里，一重启就是半截镜像 → 变砖。
     *   2. 检查 dd 的退出码 —— 旧代码用 `> /dev/null 2>&1` 把一切错误吞掉了。
     *   3. 写入前先核对容量，镜像大于分区直接拒绝。
     *   4. 写后按**完整长度**回读校验 —— 旧 OTA 只比对了 1 个 4KB 块，
     *      基本等于没校验（写坏中段照样报"校验通过"）。
     */
    suspend fun writeImage(imagePath: String, device: String): Result = withContext(Dispatchers.IO) {
        val imgSize = size(imagePath)
        if (imgSize <= 0L) return@withContext Result(false, "镜像文件为空或不存在：$imagePath")

        val partSize = partitionSize(device)
        if (partSize <= 0L) return@withContext Result(false, "无法读取分区容量：$device")
        if (imgSize > partSize) {
            return@withContext Result(
                false,
                "镜像 ${mb(imgSize)}MB 超出分区 ${mb(partSize)}MB，已拒绝写入"
            )
        }

        // dd 的退出码必须**紧跟 dd** 取，否则拿到的是后面命令的状态
        val write = run(
            "dd if='$imagePath' of='$device' bs=4096 conv=fsync 2>&1; echo __RC=${'$'}?; sync",
            WRITE_TIMEOUT
        )
        val rc = Regex("""__RC=(\d+)""").find(write.output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        if (rc != 0) {
            return@withContext Result(false, "写入失败(dd rc=$rc)：${write.output.takeLast(400)}")
        }

        val srcHash = md5File(imagePath)
            ?: return@withContext Result(false, "无法计算源镜像校验值")
        val dstHash = md5Device(device, imgSize)
            ?: return@withContext Result(false, "无法回读分区校验值")

        if (srcHash != dstHash) {
            return@withContext Result(
                false,
                "回读校验不一致（源 $srcHash / 分区 $dstHash），$device 可能已损坏"
            )
        }
        Result(true, "已写入并校验通过：${mb(imgSize)}MB → $device")
    }

    suspend fun md5File(path: String): String? =
        run("md5sum '$path' 2>/dev/null").output.substringBefore(' ').trim().takeIf { it.length == 32 }

    suspend fun md5Device(device: String, bytes: Long): String? {
        val blocks = (bytes + 4095L) / 4096L
        if (blocks <= 0L) return null
        return run("dd if='$device' bs=4096 count=$blocks 2>/dev/null | md5sum", WRITE_TIMEOUT)
            .output.substringBefore(' ').trim().takeIf { it.length == 32 }
    }

    // ── assets 投递 ─────────────────────────────────────────────────

    /** 把 assets 里的可执行文件落到目标路径并赋权。 */
    suspend fun stageAsset(
        context: Context,
        assetPath: String,
        remotePath: String,
        mode: String = "0755"
    ): Boolean = withContext(Dispatchers.IO) {
        val local = File(context.cacheDir, "stage_" + remotePath.substringAfterLast('/'))
        val copied = runCatching {
            context.assets.open(assetPath).use { input ->
                local.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
        if (!copied) return@withContext false
        run("cp '${local.absolutePath}' '$remotePath' && chmod $mode '$remotePath'").ok
    }

    // ── 内核模块选择 ────────────────────────────────────────────────

    sealed interface KoPick {
        data class Ok(
            val assetName: String,
            val branch: String,
            val vermagic: String?,
            val compatible: Boolean,
            val warning: String?
        ) : KoPick

        data class Missing(val reason: String) : KoPick
    }

    /**
     * 读取 .ko 里的 `vermagic=` 字串。
     *
     * 为什么需要：仓库里那 8 个模块，有 7 个是**同一个二进制文件**换了名字
     * （MD5 全为 aeb131ff…，vermagic 都是 `5.10.270-g15fd0b87ae8d`）。
     * 也就是说只有 5.10 和 6.12 两档是真的，其余命名的分支根本 insmod 不进去。
     * 与其让用户"刷入成功"后一脸疑惑，不如在刷之前就把这件事说清楚。
     */
    fun readVermagic(context: Context, assetPath: String): String? = runCatching {
        context.assets.open(assetPath).use { input ->
            val text = String(input.readBytes(), Charsets.ISO_8859_1)
            Regex("vermagic=([^\u0000\n]+)").find(text)?.groupValues?.get(1)?.trim()
        }
    }.getOrNull()

    /** vermagic 的 x.y.z 与运行内核是否对得上（对不上 insmod 必被拒绝）。 */
    fun isCompatible(vermagic: String?, kernelRelease: String): Boolean {
        if (vermagic.isNullOrBlank() || kernelRelease.isBlank()) return false
        val version = Regex("""(\d+\.\d+\.\d+)""").find(vermagic)?.groupValues?.get(1) ?: return false
        return kernelRelease.startsWith(version)
    }

    fun availableKos(context: Context): List<String> =
        runCatching { context.assets.list("exploit")?.filter { it.endsWith(".ko") }?.sorted() }
            .getOrNull() ?: emptyList()

    /**
     * 精确挑出与**当前运行内核**完全匹配的模块。
     *
     * 旧代码的做法是 `"HuiJuiPhantom_${branch}_${arch}.ko"` 直接拼字符串去 `assets.open()`，
     * 拼错了就是一个 FileNotFoundException 冒到最外层，用户只看到"执行异常"。
     * 更糟的是 `KernelVersionMatcher` 在 android14 + 6.6 内核上会错配成 6.1 的模块 ——
     * 版本魔数对不上，insmod 必然失败，运气差还会把内核 panic 掉。
     *
     * 现在：版本识别不出来就**明确报错并列出内置模块清单**，绝不猜。
     */
    suspend fun resolveKo(context: Context, kernelRelease: String): KoPick {
        val info = KernelVersionMatcher.matchKmi(kernelRelease, "arm64")
            ?: return KoPick.Missing("无法识别内核版本「$kernelRelease」，暂无对应的 HUI 内核模块")

        val all = availableKos(context)
        val exact = "HuiJuiPhantom_${info.branch}_${info.arch}.ko"
        if (all.contains(exact)) {
            val vermagic = readVermagic(context, "exploit/$exact")
            val compatible = isCompatible(vermagic, kernelRelease)
            val warning = if (compatible) {
                null
            } else {
                "⚠️ 内置模块 vermagic「${vermagic ?: "未知"}」与当前内核「$kernelRelease」不一致，" +
                    "insmod 很可能被内核拒绝；刷入后若仍显示未工作，说明模块没能加载。"
            }
            return KoPick.Ok(exact, info.branch, vermagic, compatible, warning)
        }

        return KoPick.Missing(
            "内置模块缺少 $exact（当前内核 $kernelRelease）\n可用模块：${all.joinToString("、").ifEmpty { "无" }}"
        )
    }

    fun mb(bytes: Long): Long = bytes / 1024L / 1024L
}
