package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HuijuiModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String
)

/**
 * 🩸 模块引擎（跨管理器读目录）
 *
 * 目录策略：
 *   - HUI 环境：读私有目录 /data/adb/huijuisu/modules + 外部目录 /data/adb/modules
 *   - 非 HUI 环境：只读外部目录 /data/adb/modules
 *
 * 为什么不直接在 ModuleListProvider 里做：
 *   ModuleListProvider 是"UI 层聚合器"（展示分组），ModuleEngine 是"底层引擎"（供其他模块调用）
 *   两者职责不同，但目录读取逻辑一致
 */
object ModuleEngine {

    suspend fun getInstalledModules(): List<HuijuiModuleInfo> = withContext(Dispatchers.IO) {
        val modules = mutableListOf<HuijuiModuleInfo>()
        val manager = RootManagerDetector.detect()

        // HUI 环境读两处，其他环境只读一处
        val dirs = when (manager) {
            RootManager.HUIJUI, RootManager.HUIJUI_ULTRA ->
                listOf("/data/adb/huijuisu/modules", "/data/adb/modules")
            else ->
                listOf("/data/adb/modules")
        }

        val seenIds = mutableSetOf<String>()

        for (dir in dirs) {
            val lsResult = HuijuiShell.exec("ls -1 $dir 2>/dev/null")
            if (!lsResult.isSuccess) continue

            for (id in lsResult.out) {
                if (id.isBlank() || id.startsWith(".") || id == "lost+found") continue
                if (!seenIds.add(id)) continue  // 去重（优先 HUI 私有目录）

                val propResult = HuijuiShell.exec("cat $dir/$id/module.prop 2>/dev/null")
                if (!propResult.isSuccess) continue

                var name = id
                var version = "未知"
                var author = "未知"
                var desc = ""

                propResult.out.forEach { line ->
                    when {
                        line.startsWith("name=") -> name = line.removePrefix("name=").trim()
                        line.startsWith("version=") -> version = line.removePrefix("version=").trim()
                        line.startsWith("author=") -> author = line.removePrefix("author=").trim()
                        line.startsWith("description=") -> desc = line.removePrefix("description=").trim()
                    }
                }
                modules.add(HuijuiModuleInfo(id, name, version, author, desc))
            }
        }
        return@withContext modules
    }
}
