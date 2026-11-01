package com.huijui.sunext.edition.core.root

import android.content.Context
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 环境隔离状态。
 *
 * ⚠️ 旧实现是假的：
 * ```kotlin
 * isPathHidden = isHuiActive
 * isMountHidden = isHuiActive
 * ```
 * 这两个字段直接等于「HUI 是否激活」，所以面板上永远显示三个绿勾，
 * 哪怕路径、设备节点、挂载表全都暴露着。
 *
 * 现在全部改成**本进程（非 root）视角**的真实测量 —— 我们的 App 跑在自己的
 * uid 下，它看到的东西就是别的普通应用看到的东西，这正是「隐藏」要防的视角。
 * 只有 `huiActive` 还需要 root 去问内核。
 */
object HuiHidingManager {

    data class HidingStatus(
        val huiActive: Boolean,
        /** true = 本进程能看见 = 没藏住 */
        val pathVisible: Boolean,
        val deviceVisible: Boolean,
        /** null = 读不到 /proc，无法判定 */
        val mountVisible: Boolean?,
        val kernelVisible: Boolean?,
        val isolatedAppCount: Int,
        val riskWarning: String?
    ) {
        /** 所有可判定的项都看不见 = 隐藏生效 */
        val allHidden: Boolean
            get() = !pathVisible && !deviceVisible && mountVisible != true && kernelVisible != true
    }

    suspend fun getStatus(context: Context): HidingStatus = withContext(Dispatchers.IO) {
        val huiActive = HuijuiShell.exec("test -c /dev/huijui && echo yes", timeoutMs = 3_000L)
            .out.joinToString("").contains("yes")

        // 下面全部是普通文件读取，走的是我们自己的 uid（非 root）
        val pathVisible = File("/data/adb/huijui").exists()
        val deviceVisible = File("/dev/huijui").exists()

        val mountVisible = runCatching {
            File("/proc/self/mountinfo").readLines().any { it.contains("huijui", ignoreCase = true) }
        }.getOrNull()

        val kernelVisible = runCatching {
            File("/proc/modules").readLines().any { it.contains("huijui", ignoreCase = true) }
        }.getOrNull()

        HidingStatus(
            huiActive = huiActive,
            pathVisible = pathVisible,
            deviceVisible = deviceVisible,
            mountVisible = mountVisible,
            kernelVisible = kernelVisible,
            isolatedAppCount = AppSettings.grantedApps.size,
            riskWarning = "⚠️ HUI 拒绝为外挂/作弊软件提供环境隐藏服务。在竞技游戏中使用 Root 环境，存在被判定违规封号的风险。"
        )
    }

    suspend fun clearMountsGlobal(): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("echo 'CLEAR_MOUNTS' > /proc/hui_config").isSuccess
    }
}
