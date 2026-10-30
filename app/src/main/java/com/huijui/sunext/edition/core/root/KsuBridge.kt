package com.huijui.sunext.edition.core.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KsuBridge {
    private const val TAG = "KsuBridge"
    private const val ALLOWLIST = "/data/adb/ksu/.allowlist"
    private const val KSUD = "/data/adb/ksud"
    private const val PKG_REGEX_STR = """^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$"""
    private val PKG_REGEX = Regex(PKG_REGEX_STR)

    suspend fun isKsuAvailable(): Boolean = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec("su -c 'test -x $KSUD && echo yes || echo no'", useCache = true, timeoutMs = 3000L)
        r.isSuccess && r.out.any { it.trim() == "yes" }
    }

    suspend fun listGranted(): Set<String> = withContext(Dispatchers.IO) {
        val r = HuijuiShell.exec("su -c 'strings $ALLOWLIST'", useCache = false, timeoutMs = 5000L)
        if (!r.isSuccess || r.out.isEmpty()) return@withContext emptySet()
        
        // 🛡️ 严格过滤，防长字符串撑爆
        r.out.map { it.trim() }
            .filter { it.isNotBlank() && it.length in 3..100 && !it.contains(':') && PKG_REGEX.matches(it) }
            .toSet()
    }

    suspend fun listModules(): List<InstalledModule> = withContext(Dispatchers.IO) {
        try {
            val r = HuijuiShell.exec("su -c '$KSUD module list'", useCache = false, timeoutMs = 10000L)
            if (!r.isSuccess || r.out.isEmpty()) return@withContext emptyList()
            
            val result = mutableListOf<InstalledModule>()
            var currentMap: MutableMap<String, String>? = null
            
            for (line in r.out) {
                val t = line.trim()
                if (t.startsWith("{")) {
                    currentMap = mutableMapOf()
                } else if (t.startsWith("}")) {
                    currentMap?.let { m ->
                        val id = m["id"]
                        if (!id.isNullOrBlank()) {
                            result.add(InstalledModule(
                                id = id,
                                name = m["name"] ?: id,
                                version = m["version"] ?: "unknown",
                                author = m["author"] ?: "unknown",
                                description = m["description"] ?: "",
                                isEnabled = m["enabled"] == "true",
                                removePending = m["remove"] == "true"
                            ))
                        }
                    }
                    currentMap = null
                } else if (currentMap != null && t.contains(":")) {
                    val key = t.substringBefore(":").trim().replace("\"", "")
                    val value = t.substringAfter(":").trim().trimEnd(',').replace("\"", "")
                    currentMap[key] = value
                }
            }
            result.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "模块解析异常: ${e.message}")
            emptyList()
        }
    }

    suspend fun install(zipPath: String): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("su -c '$KSUD module install \"$zipPath\"'", useCache = false, timeoutMs = 15_000L).isSuccess
    }

    suspend fun toggle(id: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val sub = if (enable) "enable" else "disable"
        HuijuiShell.exec("su -c '$KSUD module $sub $id'", useCache = false, timeoutMs = 5_000L).isSuccess
    }

    suspend fun uninstall(id: String): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("su -c '$KSUD module uninstall $id'", useCache = false, timeoutMs = 5_000L).isSuccess
    }
}
