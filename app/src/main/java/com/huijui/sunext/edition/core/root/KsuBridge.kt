package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 KSU 底层桥接器（隔离注入，不碰核心逻辑）
 *
 * 背景：
 * - 老 App 读 /data/adb/ksu/package_name（不存在，KSU 新版改成 .allowlist 二进制）
 * - .allowlist 是 KernelSU 内核 ABI 序列化格式，直接正则解析必翻车
 * - 正路：strings 抠 ASCII → Kotlin 侧正则过滤（避开 cmd / shell 多层引号地狱）
 * - 模块：ksud module list 官方 JSON 接口，一次调用拿全部，避免 N+1 su 卡死
 */
object KsuBridge {

    private val ALLOWLIST = "/data/adb/ksu/.allowlist"
    private val KSUD = "/data/adb/ksud"

    // 包名正则：至少一个点、每段字母开头、纯 ASCII 标识符（自动挡掉 u:r:ksu:s0）
    private val PKG_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+${'$'}""")

    // 模块 JSON 行解析（ksud 输出每对 key:value 独立一行）
    private val KV_REGEX = Regex("""^\s*"([^"]+)"\s*:\s*"(.*)"\s*,?\s*${'$'}""")

    /**
     * 🎯 判断设备是否装了 KernelSU（ksud 可执行）
     */
    suspend fun isKsuAvailable(): Boolean = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec(
            "su -c 'test -x $KSUD && echo yes || echo no'",
            useCache = true,
            timeoutMs = 10_000L
        )
        r.out.any { it.trim() == "yes" }
    }

    /**
     * 🎯 读授权包名列表
     * 1. strings 抠 ASCII（toybox 自带，零转义烦恼）
     * 2. Kotlin 正则过滤：点分标识符 + 排除含冒号的 SELinux context
     * 3. 去重
     */
    suspend fun listGranted(): Set<String> = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec(
            "su -c 'strings $ALLOWLIST'",
            useCache = false,
            timeoutMs = 10_000L
        )
        if (!r.isSuccess) return@withContext emptySet()
        r.out.map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(':') && PKG_REGEX.matches(it) }
            .toSet()
    }

    /**
     * 🎯 读已安装模块（ksud module list 官方 JSON）
     * 解析失败 / 命令不可用时返回空，调用方走老逻辑兜底
     */
    suspend fun listModules(): List<InstalledModule> = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec(
            "su -c '$KSUD module list'",
            useCache = false,
            timeoutMs = 10_000L
        )
        if (!r.isSuccess || r.out.isEmpty()) return@withContext emptyList()
        parseModuleJson(r.out)
    }

    /**
     * 🎯 启用/禁用模块（走 ksud 官方接口，替代手动 touch/rm）
     */
    suspend fun toggle(id: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val sub = if (enable) "enable" else "disable"
        HuijuiShell.exec("su -c '$KSUD module $sub $id'", useCache = false, timeoutMs = 10_000L).isSuccess
    }

    /**
     * 🎯 卸载模块（标记 remove，ksud 官方接口）
     */
    suspend fun uninstall(id: String): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("su -c '$KSUD module uninstall $id'", useCache = false, timeoutMs = 10_000L).isSuccess
    }

    // 轻量 JSON 数组解析（不引第三方库，逐行状态机）
    private fun parseModuleJson(lines: List<String>): List<InstalledModule> {
        val result = mutableListOf<InstalledModule>()
        var current: MutableMap<String, String>? = null
        for (raw in lines) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("{")) {
                current = mutableMapOf()
                continue
            }
            if (t.startsWith("}")) {
                current?.let { m ->
                    val id = m["id"]
                    if (!id.isNullOrBlank()) {
                        result.add(
                            InstalledModule(
                                id = id,
                                name = m["name"] ?: id,
                                version = m["version"] ?: "unknown",
                                author = m["author"] ?: "unknown",
                                description = unescape(m["description"] ?: ""),
                                isEnabled = m["enabled"] == "true",
                                removePending = m["remove"] == "true"
                            )
                        )
                    }
                }
                current = null
                continue
            }
            current?.let { m ->
                KV_REGEX.find(t)?.let { match ->
                    m[match.groupValues[1]] = match.groupValues[2]
                }
            }
        }
        return result.distinctBy { it.id }
    }

    // 反转义 JSON 字符串（\n、\"、\\、\t、\r、\/）
    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    else -> { sb.append(c); sb.append(s[i + 1]) }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
