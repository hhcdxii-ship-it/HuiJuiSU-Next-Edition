package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RootManager(val displayName: String, val shortName: String) {
    HUIJUI_ULTRA("HuiJuiSU Ultra", "HUI-Ultra"),
    HUIJUI("HuiJuiSU Next Edition", "HUI"),
    KERNELSU_NEXT("KernelSU Next", "KSU-Next"),
    KERNELSU("KernelSU", "KSU"),
    SUKISU_ULTRA("SukiSU-Ultra", "SukiSU"),
    BAKASU("BakaSU (ReSukiSU)", "BakaSU"),
    YUKISU("YukiSU", "YukiSU"),
    KOWSU("KowSU", "KowSU"),
    RKSU("RKSU", "RKSU"),
    MKSU("MKSU", "MKSU"),
    WILDKSU("WildKSU", "WildKSU"),
    APATCH("APatch", "APatch"),
    ASTER("Aster", "Aster"),
    FOLKPATCH("FolkPatch", "FolkPatch"),
    MAGISK("Magisk", "Magisk"),
    MAGISK_WEAVEMASK("WeaveMask", "WeaveMask"),
    MAGISK_KITSUNE("Kitsune Mask", "Kitsune"),
    MAGISK_ALPHA("Magisk Alpha", "Alpha"),
    MAGISK_DELTA("Magisk Delta", "Delta"),
    SHIZUKU("Shizuku", "Shizuku"),
    UNKNOWN("未知管理器", "Unknown"),
    NONE("无 Root", "None")
}

/**
 * 管理器识别。
 *
 * 旧实现是「一次检测最多 6 次独立 su 进程」：
 *   su -c id / su -c cat /proc/modules / su -c pm list packages / su -c ps -A
 *   su -c test -d /data/adb/ksu / su -c test -d /data/adb/apd / su -c test -d /data/adb/magisk
 * 每次都是独立 ProcessBuilder + 最长 5~10s 超时，主页一进去就卡 3 秒以上。
 *
 * 现在合并成**一次** su 会话里的多行脚本，所有信息打上 @@HUI_* 分隔符一次性取回，
 * 原来的识别规则（包名 / 进程名 / 内核模块 / adb 目录）一条不丢。
 *
 * 顺带修了一个误判：旧代码把 `modules.contains("kernelsu")` 放在包名判断之前，
 * 于是 SukiSU / KernelSU-Next 这类同样加载 `kernelsu.ko` 的变种全被标成原版 KernelSU。
 * 现在的顺序是：管理器专属包名 → 专属进程 → 专属目录 → 内核模块兜底。
 */
object RootManagerDetector {

    private const val CACHE_TTL_MS = 5_000L

    private var cachedResult: RootManager? = null
    private var cachedAt = 0L
    private val mutex = Mutex()

    private val BATCH_COMMAND: String = listOf(
        "echo '@@HUI_ID'",
        "id",
        "echo '@@HUI_MOD'",
        "cat /proc/modules 2>/dev/null",
        "echo '@@HUI_PKG'",
        "pm list packages 2>/dev/null",
        "echo '@@HUI_PS'",
        "ps -A 2>/dev/null",
        "echo '@@HUI_DIR'",
        "ls -d /data/adb/ksu /data/adb/apd /data/adb/magisk /data/adb/modules 2>/dev/null",
        "echo '@@HUI_END'"
    ).joinToString("\n")

    suspend fun detect(forceRefresh: Boolean = false): RootManager = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedResult?.let { c ->
                if (now - cachedAt < CACHE_TTL_MS) return@withContext c
            }
        }
        mutex.withLock {
            val now2 = System.currentTimeMillis()
            if (!forceRefresh) {
                cachedResult?.let { c ->
                    if (now2 - cachedAt < CACHE_TTL_MS) return@withContext c
                }
            }
            val result = doDetect()
            cachedResult = result
            cachedAt = now2
            result
        }
    }

    private suspend fun doDetect(): RootManager {
        val sections = snapshot()
        val idOutput = sections["ID"].orEmpty()
        if (!Regex("""\buid=0\b""").containsMatchIn(idOutput)) {
            return RootManager.NONE
        }

        val modules = sections["MOD"].orEmpty().lowercase()
        val packages = sections["PKG"].orEmpty().lowercase()
        val processes = sections["PS"].orEmpty().lowercase()
        val adbDirs = sections["DIR"].orEmpty().lowercase()

        return when {
            // ── HUI 本家 ─────────────────────────────────────────────
            packages.contains("com.huijui.sunext.ultra") -> RootManager.HUIJUI_ULTRA
            processes.contains("huijuisu") || processes.contains("huijuiinit") -> RootManager.HUIJUI
            adbDirs.contains("/data/adb/huijui") -> RootManager.HUIJUI

            // ── KernelSU 系（必须放在内核模块判断之前）──────────────
            packages.contains("com.rifsxd.ksunext") || processes.contains("ksunext") -> RootManager.KERNELSU_NEXT
            packages.contains("com.sukisu.ultra") || processes.contains("sukisu") -> RootManager.SUKISU_ULTRA
            packages.contains("com.bakasu.ksu") || processes.contains("bakasu") -> RootManager.BAKASU
            packages.contains("me.weishu.kernelsu") -> RootManager.KERNELSU
            packages.contains("com.rifsxd.yukisu") -> RootManager.YUKISU
            packages.contains("com.kow.kowsu") -> RootManager.KOWSU
            packages.contains("com.rksu") || packages.contains("io.github.rksu") -> RootManager.RKSU
            packages.contains("com.mksu") -> RootManager.MKSU
            packages.contains("com.wildkernels") || packages.contains("com.wildksu") -> RootManager.WILDKSU
            packages.contains("com.aster") -> RootManager.ASTER
            packages.contains("com.folkpatch") -> RootManager.FOLKPATCH

            // ── APatch ──────────────────────────────────────────────
            packages.contains("me.bmax.apatch") ||
                processes.contains("apd") ||
                adbDirs.contains("/data/adb/apd") -> RootManager.APATCH

            // ── Magisk 系（变种先判，Alpha/Delta 与官方共用包名）──────
            packages.contains("io.github.huskydg.magisk") -> RootManager.MAGISK_KITSUNE
            packages.contains("com.weavemask") -> RootManager.MAGISK_WEAVEMASK
            packages.contains("io.github.vvb2060.magisk") ||
                packages.contains("com.topjohnwu.magisk.delta") -> RootManager.MAGISK_DELTA
            packages.contains("com.topjohnwu.magisk.alpha") -> RootManager.MAGISK_ALPHA
            packages.contains("com.topjohnwu.magisk") -> RootManager.MAGISK
            processes.contains("magiskd") || adbDirs.contains("/data/adb/magisk") -> RootManager.MAGISK

            // ── 内核模块兜底 ─────────────────────────────────────────
            modules.contains("huijui") -> RootManager.HUIJUI
            modules.contains("sukisu") -> RootManager.SUKISU_ULTRA
            modules.contains("apatch") || modules.contains("kpatch") -> RootManager.APATCH
            modules.contains("kernelsu") -> RootManager.KERNELSU
            adbDirs.contains("/data/adb/ksu") -> RootManager.KERNELSU

            // ── 只有 Shizuku / 对不上号 ──────────────────────────────
            packages.contains("moe.shizuku.privileged.api") -> RootManager.SHIZUKU
            else -> RootManager.UNKNOWN
        }
    }

    /** 一次会话取回全部指纹；失败返回空 Map，绝不抛异常。 */
    private suspend fun snapshot(): Map<String, String> {
        val result = runCatching {
            HuijuiShell.exec(BATCH_COMMAND, useCache = false, timeoutMs = 6_000L)
        }.getOrNull() ?: return emptyMap()

        if (result.out.isEmpty()) return emptyMap()

        val sections = HashMap<String, StringBuilder>()
        var current: String? = null
        for (line in result.out) {
            if (line.startsWith("@@HUI_")) {
                current = line.removePrefix("@@HUI_").trim()
                sections[current] = StringBuilder()
            } else {
                current?.let { sections.getOrPut(it) { StringBuilder() }.append(line).append('\n') }
            }
        }
        return sections.mapValues { it.value.toString() }
    }
}
