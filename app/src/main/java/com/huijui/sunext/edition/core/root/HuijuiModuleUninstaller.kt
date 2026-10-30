package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HuijuiModuleUninstaller {
    // 卸载模块：删除模块目录，并触发内核热重载
    suspend fun uninstallModule(moduleId: String): Boolean = withContext(Dispatchers.IO) {
        val dir = "/data/adb/huijuisu/modules/$moduleId"
        val result = HuijuiShell.exec("rm -rf $dir")
        if (result.isSuccess) {
            // 触发热重载，从 OverlayFS 中移除
            HuijuiShell.exec("echo 'RELOAD_MODULES' > /proc/hui_config")
            true
        } else {
            false
        }
    }
}
