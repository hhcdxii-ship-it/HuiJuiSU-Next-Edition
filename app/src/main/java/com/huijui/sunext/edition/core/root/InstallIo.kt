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

        // 写之前先确认分区不是只读 —— 有些机型/ROM 会把 boot 分区 mark 成 ro，
        // 这时 dd 会以 EROFS 失败，但报错信息里看不出是"只读"。
        val ro = run("blockdev --getro '$device' 2>/dev/null").output.trim()
        if (ro == "1") {
            return@withContext Result(
                false,
                "分区处于只读状态（$device）。部分 ROM 会用 dm-verity / 只读标记保护启动分区，" +
                    "需要对 vbmeta 关闭校验后才能写入。"
            )
        }

        // 三种写法依次尝试。
        // 某些内核/ROM 上 `dd conv=fsync` 对块设备会失败（EINVAL/ENOSPC），
        // 而 `cat > 设备` 或 `dd bs=1M` 却能写进去。与其一次失败就报错，
        // 不如把三条路都走一遍，并把每条的真实输出留下来。
        val attempts = listOf(
            "dd if='$imagePath' of='$device' bs=4096 conv=fsync 2>&1; echo __RC=${'$'}?",
            "cat '$imagePath' > '$device' 2>&1; echo __RC=${'$'}?",
            "dd if='$imagePath' of='$device' bs=1M 2>&1; echo __RC=${'$'}?"
        )
        val logs = StringBuilder()
        var written = false
        for ((i, cmd) in attempts.withIndex()) {
            val out = run("$cmd; sync", WRITE_TIMEOUT)
            val rc = Regex("""__RC=(\d+)""").find(out.output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            logs.append("第${i + 1}种 (rc=$rc): ").append(out.output.trim().takeLast(200)).append('\n')
            if (rc == 0) { written = true; break }
        }
        if (!written) {
            return@withContext Result(false, "写入失败，三种方式都未能完成：\n$logs")
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
     * 这是刷写前的最后一道闸：vermagic 的分支号对不上，`insmod` 必被内核拒绝，
     * 而失败发生在**开机时**，用户只会看到"刷入成功"但状态一直"未工作"。
     * 提前把模块的真实构建目标打出来，能把这种玄学问题一次说清。
     */
    fun readVermagic(context: Context, assetPath: String): String? = runCatching {
        context.assets.open(assetPath).use { input ->
            val text = String(input.readBytes(), Charsets.ISO_8859_1)
            Regex("vermagic=([^\u0000\n]+)").find(text)?.groupValues?.get(1)?.trim()
        }
    }.getOrNull()

    /**
     * vermagic 的**分支号**（x.y）与运行内核是否一致。
     *
     * 为什么不是比 x.y.z：GKI 模块的 vermagic 带的是**构建时**的补丁号与 git hash
     * （例如 `6.1.177-g00ed7c2479b4`），而设备上跑的通常是同分支的另一个补丁号
     * （`6.1.75-android14-...`）。按整串比会把 8 个正确的模块全部误判成不兼容。
     * 真正决定能不能加载的是 KMI 分支：5.10 / 5.15 / 6.1 / 6.6 / 6.12 / 6.18。
     */
    fun isCompatible(vermagic: String?, kernelRelease: String): Boolean {
        val moduleBranch = branchOf(vermagic) ?: return false
        val kernelBranch = branchOf(kernelRelease) ?: return false
        return moduleBranch == kernelBranch
    }

    private fun branchOf(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return Regex("""(\d+)\.(\d+)""").find(raw)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
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
