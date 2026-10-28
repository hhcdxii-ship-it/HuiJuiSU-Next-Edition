package com.huijui.su.next.core.utils

import com.huijui.su.next.core.root.HuijuiShell
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

                kotlinx.coroutines.delay(10000) // 守护频率提高至 10 秒，更快响应系统重置
            }
        }
    }

    fun stopGuardian() {
        isGuardianRunning = false
        guardianJob?.cancel()
    }
}
