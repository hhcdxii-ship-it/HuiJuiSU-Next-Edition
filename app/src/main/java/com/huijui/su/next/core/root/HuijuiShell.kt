package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>,
        val durationMs: Long = 0L
    )

    private val shellMutex = Mutex()
    private val cache = mutableMapOf<String, CommandResult>()
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    suspend fun exec(
        command: String,
        useCache: Boolean = false,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult = withContext(Dispatchers.IO) {
        if (useCache && cache.containsKey(command)) {
            return@withContext cache[command]!!
        }

        return@withContext shellMutex.withLock {
            val start = System.currentTimeMillis()
            val outList = mutableListOf<String>()
            val errList = mutableListOf<String>()
            var exitCode = -1

            // 使用超时保护，防止 sh 卡死拖垮整个 App
            val completed = withTimeoutOrNull(timeoutMs) {
                try {
                    val process = ProcessBuilder("sh", "-c", command).start()
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
                    true
                } catch (e: Exception) {
                    errList.add(e.message ?: "Unknown Error")
                    false
                }
            }

            if (completed == null) {
                errList.add("命令执行超时 (${timeoutMs}ms)")
            }

            val duration = System.currentTimeMillis() - start
            val result = CommandResult(exitCode == 0, outList, errList, duration)
            if (useCache && result.isSuccess) {
                cache[command] = result
            }
            result
        }
    }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        // 这里不走缓存，避免用户刚授权完读到的是旧的失败结果
        val result = exec("id", useCache = false)
        result.isSuccess && result.out.any { it.contains("uid=0") }
    }
}
