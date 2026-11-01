package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内置内核模块矩阵。
 *
 * 之前这 8 个 .ko 在 UI 上完全不可见，用户只会在"刷完没效果"时怀疑人生。
 * 这里把「内置了哪 8 档 / 各自真实构建目标 / 哪一档匹配当前内核」摊开，
 * 配合 vermagic 一起展示，装错版本能当场看出来。
 */
object KoMatrix {

    data class Entry(
        val assetName: String,
        val branch: String,
        val sizeBytes: Long,
        val vermagic: String?,
        val matched: Boolean
    )

    data class Snapshot(
        val kernelRelease: String,
        val currentBranch: String?,
        val entries: List<Entry>
    ) {
        val matchedEntry: Entry? get() = entries.firstOrNull { it.matched }
    }

    suspend fun snapshot(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val release = KernelVersionMatcher.currentKernelRelease()
        val currentBranch = KernelVersionMatcher.matchKmi(release, "arm64")?.branch

        val entries = InstallIo.availableKos(context).map { name ->
            val branch = name
                .removePrefix("HuiJuiPhantom_")
                .removeSuffix("_arm64.ko")
                .removeSuffix(".ko")
            Entry(
                assetName = name,
                branch = branch,
                sizeBytes = assetSize(context, "exploit/$name"),
                vermagic = InstallIo.readVermagic(context, "exploit/$name"),
                matched = branch == currentBranch
            )
        }

        Snapshot(release, currentBranch, entries)
    }

    private fun assetSize(context: Context, path: String): Long = runCatching {
        context.assets.openFd(path).use { it.length }
    }.getOrElse {
        // 压缩过的 asset 拿不到 fd，退化成流计数
        runCatching {
            context.assets.open(path).use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                }
                total
            }
        }.getOrDefault(0L)
    }
}
