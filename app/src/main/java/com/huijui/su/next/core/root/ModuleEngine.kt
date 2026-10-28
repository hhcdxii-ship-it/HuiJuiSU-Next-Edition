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
 * 🩸 纯血自研模块管理器
 * 不使用任何官方数据类，直接操作文件系统
 */
object ModuleEngine {

    suspend fun getInstalledModules(): List<HuijuiModuleInfo> = withContext(Dispatchers.IO) {
        val modules = mutableListOf<HuijuiModuleInfo>()
        
        // 自己拉起 su 进程去扫描
        val lsResult = HuijuiShell.exec("ls /data/adb/modules")
        if (lsResult.isSuccess) {
            lsResult.out.forEach { id ->
                if (id.isNotBlank() && !id.startsWith(".") && !id.startsWith("lost+found")) {
                    // 自己 cat 出 prop 文件
                    val propResult = HuijuiShell.exec("cat /data/adb/modules/$id/module.prop")
                    if (propResult.isSuccess) {
                        var name = id; var version = "未知"; var author = "未知"; var desc = ""
                        propResult.out.forEach { line ->
                            when {
                                line.startsWith("name=") -> name = line.substringAfter("name=")
                                line.startsWith("version=") -> version = line.substringAfter("version=")
                                line.startsWith("author=") -> author = line.substringAfter("author=")
                                line.startsWith("description=") -> desc = line.substringAfter("description=")
                            }
                        }
                        modules.add(HuijuiModuleInfo(id, name, version, author, desc))
                    }
                }
            }
        }
        return@withContext modules
    }
}
