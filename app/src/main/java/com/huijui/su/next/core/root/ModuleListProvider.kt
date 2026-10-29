package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ModuleListProvider {

    data class ModuleEntry(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val isHuijuiModule: Boolean,
        val hasWebUi: Boolean,      // 🚀 新增：是否带 HTML
        val webUiPath: String,
        val moduleType: String = "standard"       // 🚀 新增：HTML 的绝对路径
    )

    suspend fun loadAll(): List<ModuleEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ModuleEntry>()
        val huijuiModules = parseModulesIn("/data/adb/huijuisu/modules", true)
        val externalModules = parseModulesIn("/data/adb/modules", false)
        val huijuiIds = huijuiModules.map { it.id }.toSet()
        result.addAll(huijuiModules)
        result.addAll(externalModules.filterNot { it.id in huijuiIds })
        result
    }

    private suspend fun parseModulesIn(dir: String, isHuijui: Boolean): List<ModuleEntry> {
        val entries = mutableListOf<ModuleEntry>()
        val lsResult = HuijuiShell.exec("ls -1 $dir 2>/dev/null")
        if (!lsResult.isSuccess) return entries

        for (id in lsResult.out) {
            if (id.isBlank() || id.startsWith(".") || id == "lost+found") continue

            val moduleDir = "$dir/$id"
            val propResult = HuijuiShell.exec("cat $moduleDir/module.prop 2>/dev/null")
            if (!propResult.isSuccess) continue

            var name = id; var version = "未知"; var author = "未知"; var desc = ""
            for (line in propResult.out) {
                when {
                    line.startsWith("name=") -> name = line.removePrefix("name=").trim()
                    line.startsWith("version=") -> version = line.removePrefix("version=").trim()
                    line.startsWith("author=") -> author = line.removePrefix("author=").trim()
                    line.startsWith("description=") -> desc = line.removePrefix("description=").trim()
                }
            }

            // 检测 WebUI 路径
            var hasWebUi = false
            var webUiPath = ""
            val possiblePaths = listOf(
                "$moduleDir/webroot/index.html",
                "$moduleDir/index.html",
                "$moduleDir/webui/index.html"
            )
            for (path in possiblePaths) {
                if (HuijuiShell.exec("test -f $path && echo yes || echo no").out.joinToString("").contains("yes")) {
                    hasWebUi = true
                    webUiPath = path
                    break
                }
            }

            entries.add(ModuleEntry(id, name, version, author, desc, isHuijui, hasWebUi, webUiPath, "standard"))
        }
        return entries
    }
}
