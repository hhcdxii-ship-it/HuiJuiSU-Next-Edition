package com.huijui.sunext.edition.core.utils

import android.app.Activity
import android.view.WindowManager
import android.os.Build
import android.util.Log

object HighRefreshRateHelper {
    /**
     * 🚀 强制开启设备支持的最高刷新率 (120Hz/144Hz)
     */
    fun enableMaxRefreshRate(activity: Activity) {
        val window = activity.window
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            window.windowManager.defaultDisplay
        }

        if (display != null) {
            val supportedModes = display.supportedModes
            var maxRefreshRate = 0f
            var bestModeId = -1

            for (mode in supportedModes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    bestModeId = mode.modeId
                }
            }

            val layoutParams = window.attributes
            // 设置首选刷新率
            layoutParams.preferredRefreshRate = maxRefreshRate
            
            // 直接指定 modeId 比 preferredRefreshRate 更稳妥
            // （minSdk 26，原来那个 SDK_INT >= M 的判断恒成立）
            if (bestModeId != -1) {
                layoutParams.preferredDisplayModeId = bestModeId
            }
            window.attributes = layoutParams
            Log.i("HighRefreshRate", "已强行解锁最高刷新率: $maxRefreshRate Hz")
        }
    }
}
