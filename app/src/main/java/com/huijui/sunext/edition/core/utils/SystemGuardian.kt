package com.huijui.sunext.edition.core.utils
import com.huijui.sunext.edition.core.utils.AppSettings

import com.huijui.sunext.edition.core.root.HuijuiShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SystemGuardian {
    private var isGuardianRunning = false
    private var guardianJob: kotlinx.coroutines.Job? = null

    fun startGuardian(scope: CoroutineScope) {
        if (isGuardianRunning) return
        isGuardianRunning = true
        
        guardianJob = scope.launch(Dispatchers.IO) {
            while (isGuardianRunning) {
                // 两个开关都关着就直接长睡，不做任何 shell 调用
                if (!AppSettings.isAntiDevOptionsEnabled && !AppSettings.isAntiRootLossEnabled) {
                    kotlinx.coroutines.delay(60_000)
                    continue
                }

                if (AppSettings.isAntiDevOptionsEnabled) {
                    // 🚀 针对 HyperOS OS1/OS2/OS3/OS4，强制重写开关
                    HuijuiShell.exec("settings put global development_settings_enabled 1")
                    HuijuiShell.exec("settings put global adb_enabled 1")
                    
                    // 如果检测到是小米系，可能还需要针对 MIUI 优化选项
                    if (AppSettings.isXiaomiDevice) {
                        HuijuiShell.exec("settings put secure miui_optimization 0") // 关闭 MIUI 系统优化
                    }
                }
                
                if (AppSettings.isAntiRootLossEnabled) {
                    val isRooted = HuijuiShell.isRooted()
                    if (!isRooted) {
                        // 触发 Root 丢失提示逻辑
                    }
                }

                // 10 秒一次 shell 太费电；系统设置被重置不是秒级事件，30 秒足够
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    fun stopGuardian() {
        isGuardianRunning = false
        guardianJob?.cancel()
    }
}
