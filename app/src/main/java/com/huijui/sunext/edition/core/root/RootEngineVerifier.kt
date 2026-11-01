package com.huijui.sunext.edition.core.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootEngineVerifier {

    private const val SOCKET_NAME = "\u0000huijui_ghost_socket"

    data class EngineStatus(
        val isWorking: Boolean,
        val mode: String, // "HUI", "KSU", "MAGISK", "APATCH", "NONE"
        val message: String
    )

    data class VerifyReport(
        val isActive: Boolean,
        val detail: String
    )

        suspend fun checkStatus(): EngineStatus = withContext(Dispatchers.IO) {
        // 1. 优先检查纯血 HUI 模式
        if (checkHuiSocket()) {
            return@withContext EngineStatus(true, "HUI", "✅ 纯血引擎运行中")
        }

        // 2. 只要 su 命令能出 uid=0，管它是什么管理器，直接给绿灯！
        val suCheck = suExec("su -c 'id'")
        if (suCheck.contains("uid=0") || suCheck.contains("root")) {
            return@withContext EngineStatus(true, "COMPAT", "✅ 兼容模式 (HUI Ready)")
        }

        // 3. 连 su 都调不起来，才是真的未工作
        EngineStatus(false, "NONE", "❌ 引擎未唤醒，请先获取 Root 权限")
    }

    suspend fun verifyDetailed(): VerifyReport = withContext(Dispatchers.IO) {
        val status = checkStatus()
        if (status.isWorking) {
            VerifyReport(true, "纯血模式 (${status.mode}) 运行中")
        } else {
            VerifyReport(false, status.message)
        }
    }

    private suspend fun suExec(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = StringBuilder()
            // 使用独立线程读取，防止 readText 阻塞导致 waitFor 失效
            val reader = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { out.appendLine(it) }
                } catch (e: Exception) {}
            }
            reader.start()
            
            val finished = p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                return@withContext "TIMEOUT"
            }
            reader.join(1000)
            out.toString()
        } catch (e: Exception) {
            return@withContext "ERROR: ${e.message}"
        }
    }

    private fun checkHuiSocket(): Boolean {
        return try {
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            val out = socket.outputStream
            out.write("id\n".toByteArray())
            out.flush()

            val input = socket.inputStream
            val buffer = ByteArray(1024)
            val length = input.read(buffer)
            socket.close()

            if (length > 0) {
                String(buffer, 0, length).contains("uid=0")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
