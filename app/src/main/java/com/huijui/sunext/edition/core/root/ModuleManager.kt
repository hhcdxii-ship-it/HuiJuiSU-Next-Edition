package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledModule(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isEnabled: Boolean,
    val updateAvailable: Boolean = false,
    val removePending: Boolean = false
)

object ModuleManager {
    /**
     * 模块目录。
     *
     * ⚠️ 以前漏了 HUI 自己的私有目录 `/data/adb/huijuisu/modules` ——
     * 纯血模式下用本管理器装进去的模块，列表里根本看不到（但 ModuleEngine 和
     * HuijuiModuleUninstaller 里又都写着这个路径，三处实现各说各话）。
     * HUI 目录放最前面：纯血环境应当优先展示自家模块。
     */
    private val MODULE_DIRS = listOf(
        "/data/adb/huijuisu/modules",
        "/data/adb/modules",
        "/data/adb/ksu/modules",
        "/data/adb/ap/modules"
    )

    suspend fun scanModules(): List<InstalledModule> = withContext(Dispatchers.IO) {
        // 🚀 优先走 KSU 官方 JSON
        val ksuModules = KsuBridge.listModules()
        if (ksuModules.isNotEmpty()) return@withContext ksuModules

        // 🛟 兜底走老逻辑
        val modules = mutableListOf<InstalledModule>()
        for (dir in MODULE_DIRS) {
            try {
                val listResult = HuijuiShell.exec("ls $dir")
                if (listResult.isSuccess && listResult.out.isNotEmpty()) {
                    val dirs = listResult.out.filter { it.isNotBlank() && !it.contains(":") }
                    for (moduleDir in dirs) {
                        val modulePath = "$dir/$moduleDir"
                        val propResult = HuijuiShell.exec("cat $modulePath/module.prop")
                        if (propResult.isSuccess) {
                            val propMap = parseProp(propResult.out)
                            val id = propMap["id"] ?: moduleDir
                            val name = propMap["name"] ?: id
                            val version = propMap["version"] ?: "unknown"
                            val author = propMap["author"] ?: "unknown"
                            val description = propMap["description"] ?: ""

                            val disabledResult = HuijuiShell.exec("test -f $modulePath/disable && echo yes || echo no")
                            val removeResult = HuijuiShell.exec("test -f $modulePath/remove && echo yes || echo no")

                            modules.add(
                                InstalledModule(
                                    id = id,
                                    name = name,
                                    version = version,
                                    author = author,
                                    description = description,
                                    isEnabled = !disabledResult.out.joinToString("").contains("yes"),
                                    removePending = removeResult.out.joinToString("").contains("yes")
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        modules.distinctBy { it.id }
    }

    private fun parseProp(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            if (line.contains("=")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
            }
        }
        return map
    }

    suspend fun toggleModule(moduleId: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        // 🛡️ 安全第一：非纯血 HUI 环境，绝对禁止插拔模块！
        val manager = RootManagerDetector.detect(forceRefresh = true)
        if (manager != RootManager.HUIJUI && manager != RootManager.HUIJUI_ULTRA) {
            return@withContext false
        }

        for (dir in MODULE_DIRS) {
            val path = "$dir/$moduleId"
            val exists = HuijuiShell.exec("test -d $path && echo yes || echo no").out.joinToString("").contains("yes")
            if (exists) {
                val cmd = if (enable) "rm -f $path/disable" else "touch $path/disable"
                val ok = HuijuiShell.exec(cmd).isSuccess
                if (ok) reloadModules()
                return@withContext ok
            }
        }
        false
    }

    suspend fun uninstallModule(moduleId: String): Boolean = withContext(Dispatchers.IO) {
        // 🛡️ 安全第一：非纯血 HUI 环境，绝对禁止卸载模块！
        val manager = RootManagerDetector.detect(forceRefresh = true)
        if (manager != RootManager.HUIJUI && manager != RootManager.HUIJUI_ULTRA) {
            return@withContext false
        }
        
        for (dir in MODULE_DIRS) {
            val path = "$dir/$moduleId"
            val exists = HuijuiShell.exec("test -d $path && echo yes || echo no").out.joinToString("").contains("yes")
            if (exists) {
                val ok = HuijuiShell.exec("touch $path/remove").isSuccess
                if (ok) reloadModules()
                return@withContext ok
            }
        }
        false
    }

    /** 通知内核按需重载模块挂载表（HUI 私有接口；不存在时静默忽略）。 */
    private suspend fun reloadModules() {
        HuijuiShell.exec("echo 'RELOAD_MODULES' > /proc/hui_config 2>/dev/null")
    }

    // 🚀 占位安全接口：底层不处理安装，留给纯血 UI 层去处理
    suspend fun installModule(zipPath: String): Boolean = withContext(Dispatchers.IO) {
        // 如果是兼容模式，直接拒绝
        val manager = RootManagerDetector.detect(forceRefresh = true)
        if (manager != RootManager.HUIJUI && manager != RootManager.HUIJUI_ULTRA) {
            return@withContext false
        }
        // 纯血模式下，这个方法的调用被重定向到 ModuleInstallDialog
        // 所以底层直接返回 false，表示这里没装，需要上层引导
        false
    }
}
