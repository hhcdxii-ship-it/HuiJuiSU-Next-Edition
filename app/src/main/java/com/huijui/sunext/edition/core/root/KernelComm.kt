package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KernelComm {
    // 与内核里的 HUI_DEVICE_NAME 保持一致
    private const val NODE_PATH = "/dev/msm_hw_random"

    /**
     * 下发临时 Root 授权
     */
    suspend fun grantTempRoot(uid: Int, timeoutSeconds: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // 向伪装的内核节点发送指令
            val cmd = "su -c 'echo \"grant $uid $timeoutSeconds\" > $NODE_PATH'"
            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()

            // 顺手修改节点权限，防止后续因权限死锁无法清理
            Runtime.getRuntime().exec("su -c 'chmod 777 $NODE_PATH'").waitFor()

            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
