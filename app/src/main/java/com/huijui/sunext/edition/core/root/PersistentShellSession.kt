package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
            val pb = ProcessBuilder("su")
            val p = try {
                pb.start()
            } catch (e: Exception) {
                ProcessBuilder("sh", "-c", "su").start()
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

                try {
                    val w = writer ?: throw IllegalStateException("Writer null")
                    val r = reader ?: throw IllegalStateException("Reader null")
                    
                    w.write("$command; echo \"$token:\$?\"\n")
                    w.flush()

                    val completed = withTimeoutOrNull(timeoutMs) {
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            if (line?.startsWith(token) == true) {
                                exitCode = line.substringAfter(token).trimStart(':').trim().toIntOrNull() ?: -1
                                break
                            }
                            line?.let { outList.add(it) }
                        }
                        true
                    }

                    if (completed == null) {
                        errList.add("执行超时 (${timeoutMs}ms)，底层会话已强制重置")
                        kill()
                    }
                } catch (e: Exception) {
                    errList.add("会话异常: ${e.message}")
                    kill()
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
