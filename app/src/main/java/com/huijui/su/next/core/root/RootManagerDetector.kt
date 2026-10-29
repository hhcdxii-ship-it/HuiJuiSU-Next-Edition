package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 🚀 Root 管理器统一检测器（含 HUI-Ultra 识别）
 *
 * 覆盖生态:
 *   - HUI 系（Ultra 老版 / Next 新版）
 *   - KernelSU 系（KSU / KSU-Next / SukiSU / BakaSU / YukiSU / KowSU / RKSU / MKSU / WildKSU）
 *   - APatch 系（APatch / Aster / FolkPatch）
 *   - Magisk 系（Magisk / WeaveMask / Kitsune / Alpha / Delta）
 *   - 其他（Shizuku 作为辅助）
 *
 * 检测优先级:
 *   1. HUI.ko 加载 + Ultra 包存在 → HUIJUI_ULTRA（老版优先识别）
 *   2. HUI.ko 加载 + 无 Ultra 包 → HUIJUI（Next）
 *   3. 其他管理器按优先级
 */
enum class RootManager(val displayName: String, val shortName: String) {
    /** HUI Ultra（老版） */
    HUIJUI_ULTRA("HuiJuiSU Ultra", "HUI-Ultra"),
    /** HUI Next（新版） */
    HUIJUI("HuiJuiSU Next", "HUI"),
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

    private const val ULTRA_PACKAGE = "com.huijui.sunext.ultra"
    private const val NEXT_PACKAGE = "com.huijui.su.next"

    @Volatile private var cachedResult: RootManager? = null
    @Volatile private var cachedAt: Long = 0L
    private const val CACHE_TTL_MS = 30_000L
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
        val cmd = """
            ( test -c /dev/huijui && echo "HUI_DEV:y" || echo "HUI_DEV:n" )
            ( lsmod 2>/dev/null | grep -q HUI && echo "HUI_MOD:y" || echo "HUI_MOD:n" )
            ( pm list packages 2>/dev/null | grep -q "com.huijui.sunext.ultra" && echo "ULTRA:y" || echo "ULTRA:n" )
            ( pm list packages 2>/dev/null | grep -q "com.huijui.su.next" && echo "NEXT:y" || echo "NEXT:n" )
            ( test -d /data/adb/ksu/next && echo "KSUNEXT:y" || echo "KSUNEXT:n" )
            ( test -d /data/adb/ksud && echo "KSU:y" || echo "KSU:n" )
            ( test -d /data/adb/apd && echo "AP:y" || echo "AP:n" )
            ( test -f /data/adb/magisk/kitsune && echo "MAGK:y" || echo "MAGK:n" )
            ( test -f /data/adb/magisk/alpha && echo "MAGAL:y" || echo "MAGAL:n" )
            ( test -f /data/adb/magisk/delta && echo "MAGDL:y" || echo "MAGDL:n" )
            ( test -f /data/adb/magisk/weavemask && echo "MAGWM:y" || echo "MAGWM:n" )
            ( test -d /data/adb/magisk && echo "MAG:y" || echo "MAG:n" )
            ( command -v su >/dev/null 2>&1 && echo "SU:y" || echo "SU:n" )
        """.trimIndent()

        val result = HuijuiShell.exec(cmd)
        val out = result.out.joinToString("\n")

        // 🎯 HUI.ko 存在 → 检查是 Ultra 还是 Next
        val hasHuiDevice = out.contains("HUI_DEV:y")
        val hasHuiMod = out.contains("HUI_MOD:y")
        if (hasHuiDevice || hasHuiMod) {
            return if (out.contains("ULTRA:y")) {
                RootManager.HUIJUI_ULTRA
            } else {
                RootManager.HUIJUI
            }
        }

        // 🎯 只有 Ultra 包存在但没有 HUI.ko → 用户装了 Ultra 但 HUI.ko 没加载，走其他管理器检测
        //    但为了尊重 Ultra 用户，如果什么管理器都没检测到，就报 UNKNOWN

        if (out.contains("KSUNEXT:y")) return RootManager.KERNELSU_NEXT
        if (out.contains("KSU:y")) return RootManager.KERNELSU
        if (out.contains("AP:y")) return RootManager.APATCH
        if (out.contains("MAGK:y")) return RootManager.MAGISK_KITSUNE
        if (out.contains("MAGAL:y")) return RootManager.MAGISK_ALPHA
        if (out.contains("MAGDL:y")) return RootManager.MAGISK_DELTA
        if (out.contains("MAGWM:y")) return RootManager.MAGISK_WEAVEMASK
        if (out.contains("MAG:y")) return RootManager.MAGISK
        if (out.contains("SU:y")) return RootManager.UNKNOWN
        return RootManager.NONE
    }

    // ==========================================
    // Helper
    // ==========================================

    fun getSuCommand(manager: RootManager): String = when (manager) {
        RootManager.HUIJUI_ULTRA, RootManager.HUIJUI -> "su"
        RootManager.KERNELSU_NEXT, RootManager.KERNELSU -> "ksud su"
        RootManager.APATCH, RootManager.ASTER, RootManager.FOLKPATCH -> "apd su"
        RootManager.MAGISK, RootManager.MAGISK_WEAVEMASK,
        RootManager.MAGISK_KITSUNE, RootManager.MAGISK_ALPHA, RootManager.MAGISK_DELTA -> "magisk su"
        RootManager.UNKNOWN, RootManager.NONE -> "su"
        else -> "su"
    }

    fun getModuleDir(manager: RootManager): String = when (manager) {
        RootManager.HUIJUI_ULTRA, RootManager.HUIJUI -> "/data/adb/huijuisu/modules"
        else -> "/data/adb/modules"
    }

    fun getDescription(manager: RootManager): String = when (manager) {
        RootManager.HUIJUI_ULTRA -> "HuiJuiSU Ultra 内核态接管中"
        RootManager.HUIJUI -> "HuiJuiSU Next 内核态接管中"
        RootManager.KERNELSU_NEXT -> "KernelSU Next 接管（兼容模式）"
        RootManager.KERNELSU -> "KernelSU 接管（兼容模式）"
        RootManager.APATCH -> "APatch 接管（兼容模式）"
        RootManager.MAGISK -> "Magisk 接管（兼容模式）"
        RootManager.MAGISK_KITSUNE -> "Kitsune Mask 接管（兼容模式）"
        RootManager.MAGISK_ALPHA -> "Magisk Alpha 接管（兼容模式）"
        RootManager.MAGISK_DELTA -> "Magisk Delta 接管（兼容模式）"
        RootManager.MAGISK_WEAVEMASK -> "WeaveMask 接管（兼容模式）"
        RootManager.SHIZUKU -> "Shizuku 辅助模式"
        RootManager.UNKNOWN -> "未知管理器接管（兼容模式）"
        RootManager.NONE -> "未检测到 Root"
        else -> "${manager.displayName} 接管（兼容模式）"
    }

    /**
     * 是否兼容模式（非 HUI 家族）
     */
    fun isCompatibilityMode(manager: RootManager): Boolean = when (manager) {
        RootManager.HUIJUI_ULTRA, RootManager.HUIJUI -> false
        RootManager.NONE -> false
        else -> true
    }

    fun invalidate() {
        cachedResult = null
        cachedAt = 0L
    }
}
