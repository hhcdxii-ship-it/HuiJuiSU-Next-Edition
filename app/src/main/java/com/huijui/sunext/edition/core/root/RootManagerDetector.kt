package com.huijui.sunext.edition.core.root

import android.util.Log
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

object RootManagerDetector {
    private const val TAG = "RootDetector"
    private const val CACHE_TTL_MS = 5000L
    private var cachedResult: RootManager? = null
    private var cachedAt = 0L
    private val mutex = Mutex()

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
        // 1. 基础检查：是否有 Root
        val idResult = HuijuiShell.exec("su -c id", useCache = false, timeoutMs = 10000L)
        if (!idResult.isSuccess || idResult.out.none { it.contains("uid=0") }) {
            return RootManager.NONE
        }

        // 2. 查内核模块（最准，因为这是运行时的）
        val modules = HuijuiShell.exec("su -c cat /proc/modules", useCache = false, timeoutMs = 5000L).out.joinToString("\n").lowercase()
        if (modules.contains("huijui")) return RootManager.HUIJUI
        if (modules.contains("kernelsu")) return RootManager.KERNELSU

        // 3. 查包名与进程（通过 pm 和 ps）
        val pkgs = HuijuiShell.exec("su -c pm list packages", useCache = false, timeoutMs = 5000L).out.joinToString("\n").lowercase()
        val ps = HuijuiShell.exec("su -c ps -A", useCache = false, timeoutMs = 5000L).out.joinToString("\n").lowercase()

        return when {
            // HUI 专属
            pkgs.contains("com.huijui.sunext.ultra") -> RootManager.HUIJUI_ULTRA
            ps.contains("huijuisu") || ps.contains("huijuiinit") -> RootManager.HUIJUI

            // KSU 全家桶
            pkgs.contains("com.rifsxd.ksunext") || ps.contains("ksunext") -> RootManager.KERNELSU_NEXT
            pkgs.contains("com.sukisu.ultra") || ps.contains("sukisu") -> RootManager.SUKISU_ULTRA
            pkgs.contains("com.bakasu.ksu") -> RootManager.BAKASU
            pkgs.contains("me.weishu.kernelsu") -> RootManager.KERNELSU
            // 如果有 ksu 目录，但包名不是上面这些，可能是内核自带或者遗留
            HuijuiShell.exec("su -c test -d /data/adb/ksu").isSuccess -> RootManager.KERNELSU

            // APatch
            pkgs.contains("me.bmax.apatch") || ps.contains("apd") || HuijuiShell.exec("su -c test -d /data/adb/apd").isSuccess -> RootManager.APATCH

            // Magisk
            pkgs.contains("io.github.huskydg.magisk") -> RootManager.MAGISK_KITSUNE
            pkgs.contains("com.topjohnwu.magisk") -> RootManager.MAGISK
            HuijuiShell.exec("su -c test -d /data/adb/magisk").isSuccess -> RootManager.MAGISK

            // 其他变种 KSU
            pkgs.contains("com.rifsxd.yukisu") -> RootManager.YUKISU
            pkgs.contains("com.kow.kowsu") -> RootManager.KOWSU
            pkgs.contains("com.rksu") -> RootManager.RKSU
            pkgs.contains("com.mksu") -> RootManager.MKSU
            pkgs.contains("com.wildksu") -> RootManager.WILDKSU
            pkgs.contains("com.aster") -> RootManager.ASTER
            pkgs.contains("com.folkpatch") -> RootManager.FOLKPATCH

            // Magisk 变种
            pkgs.contains("com.weavemask") -> RootManager.MAGISK_WEAVEMASK
            pkgs.contains("com.topjohnwu.magisk.delta") || pkgs.contains("io.github.vvb2060.magisk") -> RootManager.MAGISK_DELTA
            pkgs.contains("com.topjohnwu.magisk.alpha") -> RootManager.MAGISK_ALPHA

            // 仅 Shizuku 或有 su 进程但没对上号
            pkgs.contains("moe.shizuku.privileged.api") -> RootManager.SHIZUKU
            else -> RootManager.UNKNOWN
        }
    }
}
