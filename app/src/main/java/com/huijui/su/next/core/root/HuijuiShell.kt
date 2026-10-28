package com.huijui.su.next.core.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 🩸 纯血自研 Root 执行引擎（双模版：Socket 优先 + Su 降级）
 */
object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>
    )

    private const val SOCKET_PATH = "/data/adb/huijuisu/huijui.sock"
    private const val MARKER = "__HIJ_SOCKET_END__"

    /**
     * 执行命令。
     * 优先尝试连接我们的 C Daemon Socket。如果连不上，降级使用系统 su。
     */
    suspend fun exec(command: String, forceSu: Boolean = false): CommandResult = withContext(Dispatchers.IO) {
        if (forceSu) {
            return@withContext execWithSu(command)
        }

        val socketResult = execWithSocket(command)
        // 如果 Socket 连接失败（比如刚开始 daemon 没跑），降级到 su
        if (socketResult.err.any { it.contains("Socket 连接失败") }) {
            execWithSu(command)
        } else {
            socketResult
        }
    }

    private fun execWithSocket(command: String): CommandResult {
        val outList = mutableListOf<String>()
        val errList = mutableListOf<String>()
        var isSuccess = false
        var socket: LocalSocket? = null

        try {
            socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM))

            val writer = OutputStreamWriter(socket.outputStream)
            writer.write("EXEC $command\n")
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line == MARKER) {
                    val exitCode = reader.readLine()?.toIntOrNull() ?: -1
                    isSuccess = (exitCode == 0)
                    break
                }
                line?.let { outList.add(it) }
            }
        } catch (e: Exception) {
            errList.add("Socket 连接失败: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return CommandResult(isSuccess, outList, errList)
    }

    private fun execWithSu(command: String): CommandResult {
        val outList = mutableListOf<String>()
        val errList = mutableListOf<String>()
        var exitCode = -1
        try {
            val process = ProcessBuilder("su").start()
            val writer = OutputStreamWriter(process.outputStream)
            writer.write("$command\nexit\n")
            writer.flush()
            writer.close()

            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (outReader.readLine().also { line = it } != null) {
                line?.let { outList.add(it) }
            }
            outReader.close()

            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errReader.readLine().also { line = it } != null) {
                line?.let { errList.add(it) }
            }
            errReader.close()

            exitCode = process.waitFor()
        } catch (e: Exception) {
            errList.add(e.message ?: "Unknown Error")
        }
        return CommandResult(exitCode == 0, outList, errList)
    }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        // 先测 Socket，不通过再测 su
        val socketAlive = try {
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM))
            val writer = OutputStreamWriter(socket.outputStream)
            writer.write("PING\n")
            writer.flush()
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val res = reader.readLine() == "PONG"
            socket.close()
            res
        } catch (e: Exception) { false }

        if (socketAlive) return@withContext true

        // 降级测 su
        val result = execWithSu("id")
        result.isSuccess && result.out.any { it.contains("uid=0") }
    }
}
