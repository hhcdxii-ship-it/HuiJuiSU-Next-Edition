package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * 🩸 HUI Shell 引擎（持久会话 + 流式执行）
 *
 * exec()：持久 su 会话，用于短命令（getprop / lsmod / id 等）
 * execStreaming()：独立进程 + 实时回调，用于长任务（刷机 / 装模块）
 *
 * 两者区别：
 *   - exec 走 PersistentShellSession（串行、低开销）
 *   - execStreaming 走独立 ProcessBuilder（并行、实时、不能复用持久会话）
 *
 * 为什么流式不能用持久会话：
 *   持久会话是"发命令 → 等 token"模型，中途无法把输出实时推给调用方。
 *   流式必须独立进程 + 双读线程（stdout/stderr 并行，否则管道堵死）。
 */
object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>,
        val durationMs: Long = 0L
    )

    private val cache = ConcurrentHashMap<String, CommandResult>()
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    suspend fun exec(
        command: String,
        useCache: Boolean = false,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult = withContext(Dispatchers.IO) {
        if (useCache) {
            cache[command]?.let { return@withContext it }
        }
        val result = PersistentShellSession.exec(command, timeoutMs)
        if (useCache && result.isSuccess) {
            cache[command] = result
        }
        result
    }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        val result = exec("id", useCache = false)
        result.isSuccess && result.out.any { it.contains("uid=0") }
    }

    /**
     * 🚀 流式执行（长任务专用）
     *
     * @param command 要执行的命令
     * @param timeoutMs 总超时
     * @param onLine 每行输出回调（在 IO 线程调用，调用方自己切主线程更新 UI）
     * @return 命令结束时的汇总（out/err 是完整输出，供事后分析）
     *
     * 特性：
     *   - stdout / stderr 双线程并行读，避免一方堵塞
     *   - 超时自动 kill
     *   - onLine 抛异常不影响主流程
     */
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
            ProcessBuilder("sh", "-c", command).start()
        } catch (e: Exception) {
            return@withContext CommandResult(
                false, emptyList(), listOf("启动进程失败：${e.message}"), 0L
            )
        }

        // stdout 读线程
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

        // stderr 读线程
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

        // 等读线程收尾（最多 2 秒）
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
    }
}
