package com.huijui.sunext.edition.core.root

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🔋 电池健康数据提供者
 *
 * 数据来源：
 *   - Android API：BatteryManager（基础电量、充电状态）
 *   - sysfs：/sys/class/power_supply/battery/（健康、温度、容量等）
 *   - Xiaomi 专有：部分字段需要 root 读
 *
 * 提供的核心数据：
 *   - 当前电量、充电状态
 *   - 设计容量 / 实际容量（电池健康百分比）
 *   - 循环次数
 *   - 温度、电压、健康状态
 */
object BatteryHealthProvider {

    data class BatteryHealth(
        val level: Int,                    // 0-100
        val isCharging: Boolean,
        val temperature: Float,            // °C
        val voltage: Int,                  // mV
        val health: String,                // Good / Overheat / Dead ...
        val designCapacityMah: Int,        // 设计容量
        val actualCapacityMah: Int,        // 实际满容量
        val healthPercent: Int,            // 实际/设计 × 100
        val cycleCount: Int,               // 循环次数
        val chargeCounterUah: Int,         // 当前电荷量 μAh
        val technology: String,            // Li-ion / Li-poly
        val isAvailable: Boolean           // 是否能读到数据
    )

    /**
     * 获取电池完整健康数据
     */
    suspend fun getBatteryHealth(context: Context): BatteryHealth = withContext(Dispatchers.IO) {
        try {
            // 1. 通过 BatteryManager 拿基础数据（不需要 root）
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(null, filter)

            val temperatureRaw = stickyIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val temperature = temperatureRaw / 10f  // 毫摄氏度 → °C

            val voltage = stickyIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

            val status = stickyIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL

            val healthCode = stickyIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val health = when (healthCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OverVoltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            val technology = stickyIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

            // 2. 通过 sysfs 读详细数据（需 root）
            val cmd = """
                ( cat /sys/class/power_supply/battery/charge_full 2>/dev/null | sed 's/^/FULL:/' )
                ( cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null | sed 's/^/DESIGN:/' )
                ( cat /sys/class/power_supply/battery/cycle_count 2>/dev/null | sed 's/^/CYCLE:/' )
                ( cat /sys/class/power_supply/battery/charge_counter 2>/dev/null | sed 's/^/COUNTER:/' )
            """.trimIndent()

            val result = HuijuiShell.exec(cmd)

            var fullUah = 0
            var designUah = 0
            var cycleCount = 0
            var chargeCounterUah = 0
            var sysfsAvailable = false

            for (line in result.out) {
                when {
                    line.startsWith("FULL:") -> {
                        fullUah = line.removePrefix("FULL:").trim().toIntOrNull() ?: 0
                        sysfsAvailable = true
                    }
                    line.startsWith("DESIGN:") -> {
                        designUah = line.removePrefix("DESIGN:").trim().toIntOrNull() ?: 0
                        sysfsAvailable = true
                    }
                    line.startsWith("CYCLE:") -> {
                        cycleCount = line.removePrefix("CYCLE:").trim().toIntOrNull() ?: 0
                    }
                    line.startsWith("COUNTER:") -> {
                        chargeCounterUah = line.removePrefix("COUNTER:").trim().toIntOrNull() ?: 0
                    }
                }
            }

            // 转换单位：μAh → mAh
            val designMah = designUah / 1000
            val actualMah = fullUah / 1000

            val healthPercent = when {
                designMah > 0 && actualMah > 0 -> ((actualMah.toFloat() / designMah) * 100).toInt()
                else -> 0
            }

            BatteryHealth(
                level = level,
                isCharging = isCharging,
                temperature = temperature,
                voltage = voltage,
                health = health,
                designCapacityMah = designMah,
                actualCapacityMah = actualMah,
                healthPercent = healthPercent,
                cycleCount = cycleCount,
                chargeCounterUah = chargeCounterUah,
                technology = technology,
                isAvailable = sysfsAvailable || level >= 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BatteryHealth(
                level = -1,
                isCharging = false,
                temperature = 0f,
                voltage = 0,
                health = "Unknown",
                designCapacityMah = 0,
                actualCapacityMah = 0,
                healthPercent = 0,
                cycleCount = 0,
                chargeCounterUah = 0,
                technology = "Unknown",
                isAvailable = false
            )
        }
    }
}
