package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>,
        val durationMs: Long = 0L
    )

    // 缓存加个时间戳，防止内存泄漏和状态过期
    private data class CacheEntry(val result: CommandResult, val timestamp: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 30_000L // 缓存 30 秒自动过期
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    suspend fun exec(
        command: String,
        useCache: Boolean = false,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult = withContext(Dispatchers.IO) {
        if (useCache) {
            cache[command]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    return@withContext entry.result
                } else {
                    cache.remove(command)
                }
            }
        }
        val result = PersistentShellSession.exec(command, timeoutMs)
        if (useCache && result.isSuccess) {
            cache[command] = CacheEntry(result, System.currentTimeMillis())
        }
        result
    }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        val result = exec("id", useCache = false)
        result.isSuccess && result.out.any { it.contains("uid=0") }
    }

    suspend fun execStreaming(
        command: String,
        timeoutMs: Long = 5 * 60_000L,
        onLine: (String, Boolean) -> Unit = { _, _ -> }
    ): CommandResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val outList = mutableListOf<String>()
        val errList = mutableListOf<String>()
        var exitCode = -1
        var timedOut = false

        val process = try {
            // 🚀 核心修复：必须用 su 提权，否则刷机写分区绝对没权限！
            ProcessBuilder("su", "-c", command).start()
        } catch (e: Exception) {
            return@withContext CommandResult(
                false, emptyList(), listOf("启动 su 进程失败：${e.message}"), 0L
            )
        }

        val outThread = Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: break
                        synchronized(outList) { outList.add(l) }
                        runCatching { onLine(l, false) }
                    }
                }
            }
        }.apply { isDaemon = true; name = "huijui-stream-out" }

        val errThread = Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: break
                        synchronized(errList) { errList.add(l) }
                        runCatching { onLine(l, true) }
                    }
                }
            }
        }.apply { isDaemon = true; name = "huijui-stream-err" }

        outThread.start()
        errThread.start()

        val completed = withTimeoutOrNull(timeoutMs) {
            runCatching { process.waitFor() }.getOrNull()
        }

        if (completed == null) {
            timedOut = true
            runCatching { process.destroy() }
        }
        exitCode = completed ?: -1

        runCatching { outThread.join(2_000) }
        runCatching { errThread.join(2_000) }

        val duration = System.currentTimeMillis() - start
        val success = !timedOut && exitCode == 0

        if (timedOut) {
            synchronized(errList) { errList.add("命令执行超时（${timeoutMs}ms）") }
        }

        CommandResult(
            isSuccess = success,
            out = synchronized(outList) { outList.toList() },
            err = synchronized(errList) { errList.toList() },
            durationMs = duration
        )
    }

    suspend fun warmup() = withContext(Dispatchers.IO) {
        runCatching { PersistentShellSession.exec("echo ready", 5_000L) }
        Unit
    }

    fun release() {
        PersistentShellSession.release()
        cache.clear()
    }
}
