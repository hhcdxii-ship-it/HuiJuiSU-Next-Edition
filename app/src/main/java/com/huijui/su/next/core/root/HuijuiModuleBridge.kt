package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HuijuiModuleBridge {

    // 获取真实的内核状态
    suspend fun getKernelStatus(): String = withContext(Dispatchers.IO) {
        val result = HuijuiShell.exec("cat /proc/hui_status")
        result.out.joinToString("\n")
    }

    // 获取已挂载的模块列表
    suspend fun getMountedModules(): List<String> = withContext(Dispatchers.IO) {
        val result = HuijuiShell.exec("ls -1 /data/adb/huijuisu/modules/ 2>/dev/null")
        if (result.isSuccess) result.out.filter { it.isNotBlank() } else emptyList()
    }

    // 🚀 获取 HUI 专属内核日志 (只拿最后的 50 行，防止卡顿)
    suspend fun getKernelLogs(): List<String> = withContext(Dispatchers.IO) {
        val result = HuijuiShell.exec("dmesg | grep -i 'HUI' | tail -n 50")
        result.out
    }

    // 获取当前内核配置
    suspend fun getCurrentConfig(): String = withContext(Dispatchers.IO) {
        val result = HuijuiShell.exec("cat /proc/hui_config")
        result.out.joinToString("\n")
    }

    // 设置 CPU 调度器
    suspend fun setCpuGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("echo 'GOVERNOR:$governor' > /proc/hui_config").isSuccess
    }

    // 切换性能模式
    suspend fun setPerformanceMode(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val mode = if (enable) "1" else "0"
        HuijuiShell.exec("echo 'PERF_MODE:$mode' > /proc/hui_config").isSuccess
    }
}
