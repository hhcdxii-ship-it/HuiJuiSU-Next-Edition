package com.huijui.sunext.edition.core.root

import android.content.Context
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HuiHidingManager {

    data class HidingStatus(
        val isHuiActive: Boolean,
        val isPathHidden: Boolean,
        val isMountHidden: Boolean,
        val isKernelHidden: Boolean,
        val isolatedAppCount: Int,
        val riskWarning: String?
    )

    suspend fun getStatus(context: Context): HidingStatus = withContext(Dispatchers.IO) {
        val isHuiActive = HuijuiShell.exec("test -c /dev/huijui && echo yes").out.joinToString("").contains("yes")
        val isKernelHidden = HuijuiShell.exec("cat /proc/kallsyms | grep -q hui_ && echo no || echo yes").out.joinToString("").contains("yes")
        
        HidingStatus(
            isHuiActive = isHuiActive,
            isPathHidden = isHuiActive,
            isMountHidden = isHuiActive,
            isKernelHidden = isKernelHidden,
            isolatedAppCount = AppSettings.grantedApps.size,
            riskWarning = "⚠️ HUI 拒绝为外挂/作弊软件提供环境隐藏服务。在竞技游戏中使用 Root 环境，存在被判定违规封号的风险。"
        )
    }

    suspend fun clearMountsGlobal(): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("echo 'CLEAR_MOUNTS' > /proc/hui_config").isSuccess
    }
}
