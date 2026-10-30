package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🩸 合法环境检测器（性能优化版）
 *
 * 相对旧实现：
 *   - 旧：getenforce / mount / uname -r 各跑 1 次 exec，共 3 次
 *   - 新：3 个检查合并成 1 条命令，单次 exec 拿到全部结果
 *
 * 仅用于展示状态，绝不执行篡改
 */
object EnvironmentInspector {

    data class EnvReport(
        val selinux: String,
        val isMountDetected: Boolean,
        val kernelVersion: String
    )

    suspend fun getEnvironment(): EnvReport = withContext(Dispatchers.IO) {
        val combined = """
            ( getenforce 2>/dev/null | head -n1 | sed "s/^/SELINUX:/" )
            ( mount 2>/dev/null | grep -qE "/data|/system" && echo "MOUNT:y" || echo "MOUNT:n" )
            ( uname -r 2>/dev/null | sed "s/^/KERNEL:/" )
        """.trimIndent()

        val result = HuijuiShell.exec(combined)
        var selinux = "Unknown"
        var isMountDetected = false
        var kernel = "Unknown"

        for (line in result.out) {
            when {
                line.startsWith("SELINUX:") -> selinux = line.removePrefix("SELINUX:").trim().ifEmpty { "Unknown" }
                line == "MOUNT:y" -> isMountDetected = true
                line.startsWith("KERNEL:") -> kernel = line.removePrefix("KERNEL:").trim().ifEmpty { "Unknown" }
            }
        }

        EnvReport(selinux, isMountDetected, kernel)
    }
}
