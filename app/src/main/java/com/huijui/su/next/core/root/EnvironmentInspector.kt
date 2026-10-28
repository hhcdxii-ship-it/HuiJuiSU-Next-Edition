package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🩸 合法环境检测器
 * 仅用于展示状态，绝不执行篡改
 */
object EnvironmentInspector {
    data class EnvReport(
        val selinux: String,
        val isMountDetected: Boolean,
        val kernelVersion: String
    )

    suspend fun getEnvironment(): EnvReport = withContext(Dispatchers.IO) {
        val selinux = HuijuiShell.exec("getenforce").out.firstOrNull()?.trim() ?: "Unknown"
        // 检测当前是否正常挂载（非伪造，仅检测）
        val mountStatus = HuijuiShell.exec("mount | grep -E '/data|/system'").out.isNotEmpty()
        val kernel = HuijuiShell.exec("uname -r").out.firstOrNull()?.trim() ?: "Unknown"
        EnvReport(selinux, mountStatus, kernel)
    }
}
