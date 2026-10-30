package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        // 🚀 终极合并：一次 shell 调用，输出所有核心/温度/调度器
        val cmd = """
            ( cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null | sed 's/^/GOV:/' )
            ( cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null | sed 's/^/AVGOV:/' )
            ( cat /proc/hui_config 2>/dev/null | grep PERF_MODE | sed 's/^/PERF:/' )
            ( cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null | sed 's/^/TEMP:/' )
            for d in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do
                if [ -f "${'$'}d/scaling_cur_freq" ]; then
                    id=${'$'}(echo ${'$'}d | sed 's|.*/cpu||; s|/cpufreq||')
                    cur=${'$'}(cat ${'$'}d/scaling_cur_freq 2>/dev/null || echo 0)
                    max=${'$'}(cat ${'$'}d/cpuinfo_max_freq 2>/dev/null || echo 0)
                    min=${'$'}(cat ${'$'}d/cpuinfo_min_freq 2>/dev/null || echo 0)
                    echo "CORE:${'$'}id|${'$'}cur|${'$'}max|${'$'}min"
                fi
            done
        """.trimIndent()

        val result = HuijuiShell.exec(cmd, useCache = false, timeoutMs = 3000L)

        // 解析核心频率
        val cores = mutableListOf<CpuCore>()
        var governor = "unknown"
        var availGovernors = listOf<String>()
        var perfMode = false
        var temperature = 0f

        for (line in result.out) {
            when {
                line.startsWith("CORE:") -> {
                    val parts = line.removePrefix("CORE:").trim().split("|")
                    if (parts.size == 4) {
                        val id = parts[0].toIntOrNull() ?: continue
                        val cur = parts[1].toLongOrNull() ?: 0L
                        val max = parts[2].toLongOrNull() ?: 0L
                        val min = parts[3].toLongOrNull() ?: 0L
                        cores.add(CpuCore(id, cur, max, min))
                    }
                }
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
        val r2 = HuijuiShell.exec(
            "for c in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do [ -w \"${'$'}c\" ] && echo '$governor' > \"${'$'}c\" 2>/dev/null; done; echo ok",
            useCache = false,
            timeoutMs = 3000L
        )
        r2.out.joinToString("").contains("ok")
    }

    suspend fun setPerformanceMode(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val kernelRes = HuijuiModuleBridge.setPerformanceMode(enable)
        if (kernelRes) return@withContext true
        setGovernor(if (enable) "performance" else "schedutil")
    }
}
