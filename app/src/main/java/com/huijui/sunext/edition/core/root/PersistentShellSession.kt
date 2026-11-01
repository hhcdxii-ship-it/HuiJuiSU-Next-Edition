package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object PersistentShellSession {

    private val writeLock = Mutex()
    private val tokenGen = AtomicLong(0)

    @Volatile private var process: Process? = null
    @Volatile private var writer: OutputStreamWriter? = null
    @Volatile private var reader: BufferedReader? = null
    @Volatile private var stderrThread: Thread? = null
    @Volatile private var isKilled = false

    private fun isAlive(): Boolean = try {
        process?.isAlive == true && !isKilled
    } catch (_: Exception) {
        false
    }

    private fun ensureStarted(): Boolean {
        if (isAlive() && writer != null && reader != null) return true
        kill() // 确保旧资源彻底清理
        isKilled = false

        return try {
            val p = try {
                ProcessBuilder("su").start()
            } catch (e: Exception) {
                // 没有 Root 时自动降级为普通 sh
                ProcessBuilder("sh").start()
            }
            process = p
            writer = OutputStreamWriter(p.outputStream)
            reader = BufferedReader(InputStreamReader(p.inputStream))

            // 🛡️ stderr 线程防阻塞死锁
            stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(p.errorStream)).use { er ->
                        while (!isKilled && er.readLine() != null) { /* 丢弃 */ }
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; name = "hsud-stderr-drain" }
            stderrThread?.start()
            true
        } catch (e: Exception) {
            kill()
            false
        }
    }

    private fun kill() {
        isKilled = true
        // 1. 强制关闭输入输出流，打断可能正在阻塞的 readLine/write
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        writer = null
        reader = null

        // 2. 中断后台守护线程
        runCatching { stderrThread?.interrupt() }
        stderrThread = null

        // 3. 🚀 核心修复：使用 destroyForcibly 强杀，防止僵尸进程
        runCatching {
            if (process?.isAlive == true) process?.destroyForcibly()
        }
        process = null
    }

    /**
     * 执行一条命令。
     *
     * ⚠️ 这里修的是一个**会把整个 App 的 shell 能力永久锁死**的 bug：
     *
     *   旧代码用 `withTimeoutOrNull(timeoutMs) { r.readLine() }` 来兜底超时。
     *   但 `readLine()` 是阻塞 IO，协程超时**取消不了它**。一旦底层 su 不回包
     *   （用户把授权弹窗划掉、守护进程重启、命令把 shell 卡住），readLine 就永久挂起，
     *   而它正握着 `writeLock` —— 之后**任何**屏幕发起的 exec 都会排队等这把锁，永远等不到。
     *   表现就是：App 用一会儿之后，所有需要 Root 的操作全部转圈，只能杀进程。
     *
     *   修法：起一个看门狗线程，到点直接 kill()（关流 + destroyForcibly）。
     *   流被关掉之后 readLine 会抛 IOException，阻塞自然解除，锁正常释放。
     */
    suspend fun exec(command: String, timeoutMs: Long): HuijuiShell.CommandResult =
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                if (!ensureStarted()) {
                    return@withLock HuijuiShell.CommandResult(false, emptyList(), listOf("无法启动底层 su 会话"), 0L)
                }

                val token = "___HUIJUI_END_${tokenGen.incrementAndGet()}___"
                val start = System.currentTimeMillis()
                val outList = mutableListOf<String>()
                val errList = mutableListOf<String>()
                var exitCode = -1

                val timedOut = AtomicBoolean(false)
                val finished = AtomicBoolean(false)

                val watchdog = Thread({
                    try {
                        Thread.sleep(timeoutMs)
                        if (!finished.get()) {
                            timedOut.set(true)
                            kill()
                        }
                    } catch (_: InterruptedException) {
                    }
                }, "hsud-watchdog").apply { isDaemon = true }
                watchdog.start()

                try {
                    val w = writer ?: throw IllegalStateException("Writer null")
                    val r = reader ?: throw IllegalStateException("Reader null")

                    w.write("$command; echo \"$token:\$?\"\n")
                    w.flush()

                    while (true) {
                        val line = r.readLine() ?: break
                        if (line.startsWith(token)) {
                            exitCode = line.substringAfter(token).trimStart(':').trim().toIntOrNull() ?: -1
                            break
                        }
                        outList.add(line)
                    }
                } catch (e: Exception) {
                    if (timedOut.get()) {
                        errList.add("执行超时 (${timeoutMs}ms)，底层会话已强制重置")
                    } else {
                        errList.add("会话异常: ${e.message}")
                    }
                    kill()
                } finally {
                    finished.set(true)
                    watchdog.interrupt()
                }

                if (timedOut.get() && errList.isEmpty()) {
                    errList.add("执行超时 (${timeoutMs}ms)，底层会话已强制重置")
                }

                HuijuiShell.CommandResult(
                    isSuccess = exitCode == 0,
                    out = outList,
                    err = errList,
                    durationMs = System.currentTimeMillis() - start
                )
            }
        }

    fun release() {
        kill()
    }
}
