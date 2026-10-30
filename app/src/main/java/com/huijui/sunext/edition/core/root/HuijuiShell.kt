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

    private data class CacheEntry(val result: CommandResult, val timestamp: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 30_000L
    private const val DEFAULT_TIMEOUT_MS = 15_000L
    private const val MAX_CACHE_SIZE = 100
    // 🛡️ 流式输出防 OOM 阀值
    private const val MAX_STREAM_LINES = 2000 

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
            // 缓存容量控制，防止无限膨胀
            if (cache.size > MAX_CACHE_SIZE) cache.clear()
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
        // 使用 CircularFifoQueue 类似逻辑或者手动控制大小
        val outList = mutableListOf<String>()
        val errList = mutableListOf<String>()
        var exitCode = -1
        var timedOut = false
        var process: Process? = null

        try {
            process = ProcessBuilder("su", "-c", command).start()
        } catch (e: Exception) {
            return@withContext CommandResult(false, emptyList(), listOf("启动进程失败：${e.message}"), 0L)
        }

        val p = process
        val outThread = Thread {
            try {
                BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: break
                        synchronized(outList) {
                            if (outList.size >= MAX_STREAM_LINES) outList.removeAt(0) // 🛡️ 丢弃旧数据
                            outList.add(l)
                        }
                        runCatching { onLine(l, false) }
                    }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true }

        val errThread = Thread {
            try {
                BufferedReader(InputStreamReader(p.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: break
                        synchronized(errList) {
                            if (errList.size >= MAX_STREAM_LINES) errList.removeAt(0)
                            errList.add(l)
                        }
                        runCatching { onLine(l, true) }
                    }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true }

        outThread.start()
        errThread.start()

        val completed = withTimeoutOrNull(timeoutMs) {
            runCatching { p.waitFor() }.getOrNull()
        }

        if (completed == null) {
            timedOut = true
            // 🚀 核心修复：强杀 + 资源打断
            runCatching { p.destroyForcibly() }
            runCatching { outThread.interrupt() }
            runCatching { errThread.interrupt() }
        } else {
            exitCode = completed
        }

        // 给子线程一点时间收尾，但绝不无限等待
        runCatching { outThread.join(1000) }
        runCatching { errThread.join(1000) }

        val duration = System.currentTimeMillis() - start
        if (timedOut) {
            synchronized(errList) { errList.add("执行超时 (${timeoutMs}ms)，已强杀") }
        }

        CommandResult(
            isSuccess = !timedOut && exitCode == 0,
            out = synchronized(outList) { outList.toList() },
            err = synchronized(errList) { errList.toList() },
            durationMs = duration
        )
    }

    suspend fun warmup() = withContext(Dispatchers.IO) {
        runCatching { PersistentShellSession.exec("echo ready", 5_000L) }
    }

    fun release() {
        PersistentShellSession.release()
        cache.clear()
    }
}
