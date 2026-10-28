package com.huijui.su.next.core.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 🩸 纯血自研 Root 执行引擎（Socket 通信版）
 * 彻底抛弃系统 su，直接连我们自己的 huijui_ksud daemon 的 Unix Socket！
 */
object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>
    )

    private const val SOCKET_PATH = "/data/adb/huijuisu/huijui.sock"
    private const val MARKER = "__HIJ_SOCKET_END__"

    suspend fun exec(command: String): CommandResult = withContext(Dispatchers.IO) {
        val outList = mutableListOf<String>()
        val errList = mutableListOf<String>()
        var isSuccess = false

        var socket: LocalSocket? = null
        try {
            socket = LocalSocket()
            // 注意：Android 的 LocalSocket 默认使用 Linux Abstract 命名空间
            // 如果要连文件路径的 socket，必须用 LocalSocketAddress.Namespace.FILESYSTEM
            socket.connect(LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM))

            val writer = OutputStreamWriter(socket.outputStream)
            writer.write("EXEC $command\n")
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line == MARKER) {
                    // 读到结束标记，下一行是退出码
                    val exitCode = reader.readLine()?.toIntOrNull() ?: -1
                    isSuccess = (exitCode == 0)
                    break
                }
                line?.let { outList.add(it) }
            }
        } catch (e: Exception) {
            errList.add(e.message ?: "Socket 连接失败: 守护进程未启动?")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }

        CommandResult(isSuccess, outList, errList)
    }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        var socket: LocalSocket? = null
        try {
            socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM))
            val writer = OutputStreamWriter(socket.outputStream)
            writer.write("PING\n")
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val response = reader.readLine()
            return@withContext response == "PONG"
        } catch (e: Exception) {
            return@withContext false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
