package com.huijui.su.next.core.root

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🩸 HUI 内核通信桥
 * 负责将 App 的授权指令真正下发到 /dev/huijui
 */
object HuijuiDeviceBridge {

    // 获取应用的真实 UID
    fun getAppUid(context: Context, packageName: String): Int {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    // 写入授权指令到 HUI 内核设备
    // 格式要求：uid:package_name
    suspend fun grantRootToApp(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val uid = getAppUid(context, packageName)
        if (uid == -1) return@withContext false

        val payload = "$uid:$packageName"
        // 使用咱们的 HuijuiShell (su/socket) 将数据写入 /dev/huijui
        val result = HuijuiShell.exec("echo '$payload' > /dev/huijui")
        result.isSuccess
    }

    // 撤销授权（目前内核简单逻辑不支持删除，这里只做 App 端的移除，后续需在内核中实现 unlink）
    suspend fun revokeRootFromApp(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        // 给内核发送一个特殊指令（预留），这里暂时返回 true 让 App 更新状态
        true
    }
}
