package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CpuInfoProvider {

    data class CpuCore(val id: Int, val curFreq: Long, val maxFreq: Long, val minFreq: Long)

    data class CpuStatus(
        val cores: List<CpuCore>,
        val currentGovernor: String,
        val availableGovernors: List<String>,
        val temperature: Float,
        val performanceMode: Boolean,
        val bigCoreMaxFreq: Long,
        val littleCoreMaxFreq: Long,
        val isKernelAvailable: Boolean
    )

    suspend fun getStatus(): CpuStatus = withContext(Dispatchers.IO) {
        val cmd = """
            ( cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null | sed 's/^/GOV:/' )
            ( cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null | sed 's/^/AVGOV:/' )
            ( cat /proc/hui_config 2>/dev/null | grep PERF_MODE | sed 's/^/PERF:/' )
            ( cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null | sed 's/^/TEMP:/' )
            ( ls /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq 2>/dev/null | sed 's/^/LIST:/' )
        """.trimIndent()

        val result = HuijuiShell.exec(cmd)
        
        // 逐个解析核心频率
        val cores = mutableListOf<CpuCore>()
        val paths = result.out.filter { it.startsWith("LIST:") }.map { it.removePrefix("LIST:").trim() }
        for (path in paths) {
            val idStr = Regex("cpu(\\d+)").find(path)?.groupValues?.get(1) ?: continue
            val id = idStr.toIntOrNull() ?: continue
            val cur = HuijuiShell.exec("cat $path 2>/dev/null").out.firstOrNull()?.toLongOrNull() ?: 0L
            val max = HuijuiShell.exec("cat /sys/devices/system/cpu/cpu$id/cpufreq/cpuinfo_max_freq 2>/dev/null").out.firstOrNull()?.toLongOrNull() ?: 0L
            val min = HuijuiShell.exec("cat /sys/devices/system/cpu/cpu$id/cpufreq/cpuinfo_min_freq 2>/dev/null").out.firstOrNull()?.toLongOrNull() ?: 0L
            cores.add(CpuCore(id, cur, max, min))
        }

        var governor = "unknown"
        var availGovernors = listOf<String>()
        var perfMode = false
        var temperature = 0f

        for (line in result.out) {
            when {
                line.startsWith("GOV:") -> governor = line.removePrefix("GOV:").trim()
                line.startsWith("AVGOV:") -> availGovernors = line.removePrefix("AVGOV:").trim().split(" ").filter { it.isNotBlank() }
                line.startsWith("PERF:") -> perfMode = line.substringAfterLast(":").trim() == "1"
                line.startsWith("TEMP:") -> {
                    val raw = line.removePrefix("TEMP:").trim().toLongOrNull() ?: 0L
                    temperature = if (raw > 1000) raw / 1000f else raw.toFloat()
                }
            }
        }

        val allMaxFreqs = cores.map { it.maxFreq }.filter { it > 0 }
        val bigCoreMax = allMaxFreqs.maxOrNull() ?: 0L
        val littleCoreMax = allMaxFreqs.minOrNull() ?: 0L

        CpuStatus(cores, governor, availGovernors, temperature, perfMode, bigCoreMax, littleCoreMax, result.out.isNotEmpty())
    }

    suspend fun setGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        val kernelRes = HuijuiModuleBridge.setCpuGovernor(governor)
        if (kernelRes) return@withContext true
        val r2 = HuijuiShell.exec("for c in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do [ -w \"\$c\" ] && echo '$governor' > \"\$c\" 2>/dev/null; done; echo ok")
        r2.out.joinToString("").contains("ok")
    }

    suspend fun setPerformanceMode(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val kernelRes = HuijuiModuleBridge.setPerformanceMode(enable)
        if (kernelRes) return@withContext true
        setGovernor(if (enable) "performance" else "schedutil")
    }
}
