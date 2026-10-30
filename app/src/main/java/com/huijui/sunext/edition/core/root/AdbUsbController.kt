package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdbUsbController {

    /**
     * 🚀 强开 ADB 与 USB 调试
     * @return 返回执行的结果日志
     */
    suspend fun forceEnableAdb(context: Context): String = withContext(Dispatchers.IO) {
        val logs = StringBuilder()
        try {
            logs.append("> [环境] 正在接管硬件调试通道...\n")

            // 1. 开启全局 ADB 调试开关
            val cmd1 = "settings put global adb_enabled 1"
            logs.append(if (execSu(cmd1)) "✅ 成功开启全局 ADB 开关\n" else "❌ 开启全局 ADB 失败\n")

            // 2. 配置 USB 模式为 ADB + MTP (既保留文件传输又保留调试)
            // 如果要求纯 ADB，可以改为 adb
            val cmd2 = "setprop persist.sys.usb.config mtp,adb"
            logs.append(if (execSu(cmd2)) "✅ 成功设置 USB 模式为 MTP+ADB\n" else "❌ 设置 USB 模式失败\n")

            // 3. 解除可能需要确认的授权限制 (部分系统有)
            val cmd3 = "settings put secure adb_notify 1"
            execSu(cmd3) // 这个不强制要求成功

            // 4. 强制写入万能 ADB 公钥 (可选，意味着只要你电脑上有对应私钥，连上免弹窗)
            // 注意：为了安全，这里只是演示，我们需要先准备一个公钥字符串
            val pubKey = "your_adb_public_key_here" // 这里需要替换为真实公钥
            if (pubKey != "your_adb_public_key_here") {
                val cmd4 = """
                    mkdir -p /data/misc/adb
                    chmod 700 /data/misc/adb
                    echo "$pubKey" > /data/misc/adb/adb_keys
                    chown system:shell /data/misc/adb/adb_keys
                    chmod 640 /data/misc/adb/adb_keys
                """.trimIndent()
                if (execSu(cmd4)) logs.append("✅ 成功注入 ADB 授权公钥\n")
            }

            // 5. 重启 adbd 服务，让配置生效
            logs.append("> [服务] 正在重启 adbd 守护进程...\n")
            val cmd5 = "stop adbd; start adbd"
            if (execSu(cmd5)) logs.append("✅ adbd 服务已重启，请检查电脑连接\n") else logs.append("⚠️ adbd 重启异常，可能需要拔插数据线\n")

        } catch (e: Exception) {
            logs.append("❌ 异常: ${e.message}\n")
        }
        logs.toString()
    }

    /**
     * 🛡️ 恢复原状（关闭调试，避免长期开启被攻击）
     */
    suspend fun restoreAdb(): String = withContext(Dispatchers.IO) {
        val logs = StringBuilder()
        logs.append("> [环境] 正在恢复硬件调试通道...\n")
        execSu("settings put global adb_enabled 0")
        execSu("setprop persist.sys.usb.config mtp")
        logs.append("✅ 已关闭 ADB 调试，USB 模式恢复为 MTP\n")
        logs.toString()
    }

    private fun execSu(cmd: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
