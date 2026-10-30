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
        // 1. 基础检查：有没有 Root (第一次跑会弹 KSU 授权框)
        val idResult = HuijuiShell.exec("su -c id", useCache = false, timeoutMs = 10000L)
        if (!idResult.isSuccess || idResult.out.none { it.contains("uid=0") }) {
            Log.e(TAG, "Root check failed: isSuccess=${idResult.isSuccess}, out=${idResult.out}, err=${idResult.err}")
            return RootManager.NONE
        }

        // 2. 特征目录扫描 (先跑文件检测，比 pm list 快且准)
        val pathCmd = """
            su -c "test -d /data/adb/ksu && echo 'KSU:y' || echo 'KSU:n'"
            su -c "test -f /data/adb/ksu/KernelSU_NULL && echo 'KSUNULL:y' || echo 'KSUNULL:n'"
            su -c "test -d /data/adb/ksu/next && echo 'KSUNEXT:y' || echo 'KSUNEXT:n'"
            su -c "test -d /data/adb/apd && echo 'AP:y' || echo 'AP:n'"
            su -c "test -d /data/adb/magisk && echo 'MAG:y' || echo 'MAG:n'"
        """.trimIndent()
        val pathResult = HuijuiShell.exec(pathCmd, useCache = false, timeoutMs = 5000L).out.joinToString("\n")

        // 3. 暴力扫包名
        val pmResult = HuijuiShell.exec("su -c pm list packages", useCache = false, timeoutMs = 5000L)
        val packages = pmResult.out.joinToString("\n").lowercase()

        return when {
            // HUI 专属（但绝不能包含咱们自己的包名 com.huijui.sunext.edition）
            packages.contains("com.huijui.sunext.ultra") -> RootManager.HUIJUI_ULTRA

            // KSU 全家桶特征（KernelSU_NULL 本质上是 KSU 的变种，有 /data/adb/ksu 就算）
            pathResult.contains("KSU:y") || pathResult.contains("KSUNULL:y") -> RootManager.KERNELSU
            packages.contains("me.weishu.kernelsu") -> RootManager.KERNELSU
            pathResult.contains("KSUNEXT:y") -> RootManager.KERNELSU_NEXT
            packages.contains("com.rifsxd.ksunext") -> RootManager.KERNELSU_NEXT
            packages.contains("com.sukisu.ultra") -> RootManager.SUKISU_ULTRA
            packages.contains("com.bakasu.ksu") -> RootManager.BAKASU

            // APatch / Magisk
            packages.contains("me.bmax.apatch") || pathResult.contains("AP:y") -> RootManager.APATCH
            packages.contains("com.topjohnwu.magisk") || pathResult.contains("MAG:y") -> RootManager.MAGISK

            // 只要能拿到 uid=0 但什么都没识别出来
            else -> RootManager.UNKNOWN
        }
    }
}
