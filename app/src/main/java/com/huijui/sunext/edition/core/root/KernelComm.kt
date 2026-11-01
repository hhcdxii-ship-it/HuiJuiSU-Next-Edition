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
            // ⚠️ 这里以前是 Runtime.exec("su -c 'echo \"...\" > node'")。
            //    Runtime.exec 的单字符串重载会**按空白粗暴切分**，引号原样传给 su，
            //    实际执行的是 `su -c 'echo` + 一堆散参数 —— 命令根本不成立，
            //    临时 Root 授权从来没成功过。必须用数组形式。
            val script = "echo \"grant $uid $timeoutSeconds\" > $NODE_PATH"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val exitCode = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

            // 顺手修改节点权限，防止后续因权限死锁无法清理
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "chmod 777 $NODE_PATH"))
                .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)

            exitCode
        } catch (e: Exception) {
            false
        }
    }
}
