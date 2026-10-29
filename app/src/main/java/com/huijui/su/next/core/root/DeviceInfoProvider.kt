package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 🚀 设备信息统一提供者（性能优化版）
 *
 * 相对旧实现（ToolboxScreen 内联 6 次 exec）：
 *   - 旧：brand / model / androidVer / kernelVer / fingerprint / selinux 各 1 次 exec，共 6 次
 *   - 新：6 个 getprop 合并成 1 条命令，单次 exec 全部拿到
 *   - 加 5 秒缓存，连续刷新（点击能量球）不重复跑命令
 *
 * 使用方式（第 8 条卡片重排时接入 ToolboxScreen）：
 *   val info = DeviceInfoProvider.get()
 *   val info = DeviceInfoProvider.get(forceRefresh = true)  // 强制刷新
 *   DeviceInfoProvider.invalidate()                          // 主动失效
 */
object DeviceInfoProvider {

    data class DeviceInfo(
        val brand: String = "未知",
        val model: String = "未知",
        val androidVer: String = "未知",
        val kernelVer: String = "未知",
        val fingerprint: String = "未知",
        val selinux: String = "Unknown"
    )

    private const val CACHE_TTL_MS = 5_000L
    private val lock = Mutex()

    @Volatile private var cached: DeviceInfo? = null
    @Volatile private var cachedAt: Long = 0L

    suspend fun get(forceRefresh: Boolean = false): DeviceInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cached?.let { c ->
                if (now - cachedAt < CACHE_TTL_MS) return@withContext c
            }
        }
        lock.withLock {
            // 双检：等锁期间别的协程可能已经刷新了
            val now2 = System.currentTimeMillis()
            if (!forceRefresh) {
                cached?.let { c ->
                    if (now2 - cachedAt < CACHE_TTL_MS) return@withLock c
                }
            }
            val fresh = fetchFresh()
            cached = fresh
            cachedAt = now2
            fresh
        }
    }

    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    private suspend fun fetchFresh(): DeviceInfo {
        // 6 个 getprop 合并成 1 条命令，每个字段用唯一 KEY: 前缀
        // 用 getprop 分组括号避免单个字段失败污染其他字段
        val combined = """
            ( getprop ro.product.brand 2>/dev/null | sed "s/^/BRAND:/" )
            ( getprop ro.product.model 2>/dev/null | sed "s/^/MODEL:/" )
            ( getprop ro.build.version.release 2>/dev/null | sed "s/^/ANDROID:/" )
            ( uname -r 2>/dev/null | sed "s/^/KERNEL:/" )
            ( getprop ro.build.fingerprint 2>/dev/null | sed "s/^/FINGERPRINT:/" )
            ( getenforce 2>/dev/null | head -n1 | sed "s/^/SELINUX:/" )
        """.trimIndent()

        val result = HuijuiShell.exec(combined)

        var brand = "未知"
        var model = "未知"
        var androidVer = "未知"
        var kernelVer = "未知"
        var fingerprint = "未知"
        var selinux = "Unknown"

        for (line in result.out) {
            when {
                line.startsWith("BRAND:") -> brand = line.removePrefix("BRAND:").trim().ifEmpty { "未知" }
                line.startsWith("MODEL:") -> model = line.removePrefix("MODEL:").trim().ifEmpty { "未知" }
                line.startsWith("ANDROID:") -> androidVer = line.removePrefix("ANDROID:").trim().ifEmpty { "未知" }
                line.startsWith("KERNEL:") -> kernelVer = line.removePrefix("KERNEL:").trim().ifEmpty { "未知" }
                line.startsWith("FINGERPRINT:") -> fingerprint = line.removePrefix("FINGERPRINT:").trim().ifEmpty { "未知" }
                line.startsWith("SELINUX:") -> selinux = line.removePrefix("SELINUX:").trim().ifEmpty { "Unknown" }
            }
        }

        return DeviceInfo(brand, model, androidVer, kernelVer, fingerprint, selinux)
    }
}
