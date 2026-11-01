package com.huijui.sunext.edition.core.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object RootEngineVerifier {

    private const val SOCKET_NAME = "\u0000huijui_ghost_socket"
    private const val SOCKET_TIMEOUT_MS = 800
    private const val SU_TIMEOUT_SEC = 6L

    private val UID_ZERO = Regex("""\buid=0\b""")

    data class EngineStatus(
        val isWorking: Boolean,
        val mode: String, // "HUI", "COMPAT", "NONE"
        val message: String
    )

    data class VerifyReport(
        val isActive: Boolean,
        val detail: String
    )

    suspend fun checkStatus(): EngineStatus = withContext(Dispatchers.IO) {
        // 1. 纯血 HUI：守护进程 socket 能直接回 uid=0
        if (checkHuiSocket()) {
            return@withContext EngineStatus(true, "HUI", "✅ 纯血引擎运行中")
        }

        // 2. 兜底：只要 su 真能拿到 uid=0 就放行
        val suOut = suExec("id")
        if (UID_ZERO.containsMatchIn(suOut)) {
            return@withContext EngineStatus(true, "COMPAT", "✅ 兼容模式 (HUI Ready)")
        }

        // 3. 连 su 都调不起来
        val message = when {
            suOut == "TIMEOUT" -> "⏱ 引擎响应超时，请稍后重试"
            suOut.startsWith("ERROR") -> "❌ 引擎未唤醒，请先获取 Root 权限"
            else -> "❌ 引擎未唤醒，请先获取 Root 权限"
        }
        EngineStatus(false, "NONE", message)
    }

    suspend fun verifyDetailed(): VerifyReport = withContext(Dispatchers.IO) {
        val status = checkStatus()
        if (status.isWorking) {
            VerifyReport(true, "纯血模式 (${status.mode}) 运行中")
        } else {
            VerifyReport(false, status.message)
        }
    }

    /**
     * 旧实现有两个坑：
     *   a) 调用方传的是 "su -c 'id'"，而本函数自己又拼了一层 `su -c`，
     *      实际执行的是 `su -c "su -c 'id'"` —— 嵌套 su，慢一倍还容易被策略拦。
     *   b) 判定用 `contains("root")`，任何带 root 字样的输出（包括报错）都会误判成功。
     */
    private suspend fun suExec(cmd: String): String = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            process = p
            val out = StringBuilder()
            val reader = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { out.appendLine(it) }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true }
            reader.start()

            val finished = p.waitFor(SU_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                return@withContext "TIMEOUT"
            }
            reader.join(600)
            out.toString()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        } finally {
            runCatching { if (process?.isAlive == true) process?.destroyForcibly() }
        }
    }

    /**
     * 旧实现：connect 之后直接 `input.read(buffer)`，**没有设 SO_TIMEOUT**。
     * 守护进程只要 accepts 但不回包（崩溃重启中 / 半开连接），这里就永久阻塞，
     * 而它又被包在 withContext(IO) 里、外层没有任何超时 —— 整个 Root 检测链路卡死。
     */
    private fun checkHuiSocket(): Boolean {
        var socket: LocalSocket? = null
        return try {
            val s = LocalSocket()
            socket = s
            s.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            s.setSoTimeout(SOCKET_TIMEOUT_MS)

            s.outputStream.apply {
                write("id\n".toByteArray())
                flush()
            }

            val buffer = ByteArray(4096)
            var total = 0
            // 一次 read 可能只拿到半包，累积到有内容或读满为止
            while (total < buffer.size) {
                val n = s.inputStream.read(buffer, total, buffer.size - total)
                if (n <= 0) break
                total += n
                if (total >= 8) break
            }
            total > 0 && UID_ZERO.containsMatchIn(String(buffer, 0, total))
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }
}
