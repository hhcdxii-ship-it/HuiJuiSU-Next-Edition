import os
from pathlib import Path

print("🚀 正在注入终极精准检测器与杂鱼显示优化...")

# ==========================================
# 1. 全量重写 RootManagerDetector.kt
# ==========================================
detector_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/RootManagerDetector.kt')

detector_code = '''package com.huijui.sunext.edition.core.root

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
    APATCH("APatch", "APatch"),
    MAGISK("Magisk", "Magisk"),
    MAGISK_KITSUNE("Kitsune Mask", "Kitsune"),
    MAGISK_ALPHA("Magisk Alpha", "Alpha"),
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
        val modules = HuijuiShell.exec("su -c cat /proc/modules", useCache = false, timeoutMs = 5000L).out.joinToString("\\n").lowercase()
        if (modules.contains("huijui")) return RootManager.HUIJUI
        if (modules.contains("kernelsu")) return RootManager.KERNELSU

        // 3. 查包名与进程（通过 pm 和 ps）
        val pkgs = HuijuiShell.exec("su -c pm list packages", useCache = false, timeoutMs = 5000L).out.joinToString("\\n").lowercase()
        val ps = HuijuiShell.exec("su -c ps -A", useCache = false, timeoutMs = 5000L).out.joinToString("\\n").lowercase()

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

            // 仅 Shizuku 或有 su 进程但没对上号
            pkgs.contains("moe.shizuku.privileged.api") -> RootManager.SHIZUKU
            else -> RootManager.UNKNOWN
        }
    }
}
'''
detector_path.write_text(detector_code)
print("✅ RootManagerDetector.kt 已重写：实现绝对准确的优先级与运行时检测！")

# ==========================================
# 2. 修改 ToolboxScreen.kt 的状态显示与杂鱼映射
# ==========================================
# 寻找 ToolboxScreen.kt 中映射状态的逻辑
ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/ToolboxScreen.kt')
if ui_path.exists():
    content = ui_path.read_text()
    
    # 我们直接找到其中给 managerTitle 赋值的 when 语句进行替换
    import re
    pattern = r'ManagerState\.WORKING_HUIJUI -> ".*?"\n.*?ManagerState\.WORKING_ULTRA -> ".*?"\n.*?ManagerState\.WORKING_COMPAT -> ".*?"\n.*?ManagerState\.NOT_WORKING -> ".*?"'
    
    replacement = '''ManagerState.WORKING_HUIJUI -> "工作中 (HUI)"
                ManagerState.WORKING_ULTRA -> "工作中 (HUI-Ultra)"
                ManagerState.WORKING_COMPAT -> {
                    val compatName = detected?.shortName ?: "未知"
                    "工作中 ($compatName 兼容)"
                }
                ManagerState.NOT_WORKING -> "未工作"'''
                
    new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)
    if new_content != content:
        ui_path.write_text(new_content)
        print("✅ ToolboxScreen.kt 的状态显示逻辑已修复，现在会精准显示：工作中 (KSU 兼容) / 工作中 (Magisk 兼容) 等")
    else:
        print("⚠️ 没找到完全匹配的 when 语句，请检查 ToolboxScreen.kt 中的 ManagerState 枚举。")

print("🎉 检测与显示优化完成！直接执行 ./gradlew assembleDebug 起飞！")
