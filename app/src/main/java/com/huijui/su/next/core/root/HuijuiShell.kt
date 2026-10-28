package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 🩸 纯血自研 Root 执行引擎（长连接版）
 *
 * 核心升级：
 * 1. 常驻一个 su 进程，只拉起一次，后续所有命令复用，不再每条 fork。
 * 2. 命令结束标记 `__HIJ_CMD_DONE_<id>__<exitCode>`，读流读到标记才算完，杜绝粘包。
 * 3. Mutex 串行化，多协程并发调用不乱序。
 * 4. 独立线程阻塞读 + join 超时，超时强制销毁会话，下次自动重建。
 * 5. stderr 由后台线程持续 drain 进队列，避免 pipe buffer 写满把 su 卡死。
 * 6. StateFlow<Boolean> isAlive，UI 可实时感知 shell 存活。
 *
 * API 完全兼容旧版：exec(command): CommandResult / isRooted(): Boolean
 * 新增可选项：exec 的 timeoutMs 参数、isAlive、close()
 */
object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>
    )

    class ShellTimeoutException(message: String) : Exception(message)

    private class Session(
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader,
        val stderrQueue: LinkedBlockingQueue<String>
    )

    private val mutex = Mutex()
    @Volatile
    private var session: Session? = null
    private val seq = AtomicLong(0)

    private val _isAlive = MutableStateFlow(false)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val MARKER_PREFIX = "__HIJ_CMD_DONE_"

    /**
     * 长连接执行 Root 命令
     * @param timeoutMs 单条命令超时，超时会强杀会话并重建，默认 30s
     */
    suspend fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val s = ensureSession()
                ?: return@withLock CommandResult(
                    false, emptyList(), listOf("无法拉起 su 进程，请检查 Root 授权")
                )

            val id = seq.incrementAndGet()
            val outList = mutableListOf<String>()
            val errList = mutableListOf<String>()

            try {
                writeCommand(s, command, id)
                val exitCode = executeRead(s, id, timeoutMs, outList)
                drainStderr(s, errList)
                CommandResult(exitCode == 0, outList, errList)
            } catch (e: Exception) {
                destroyLocked()
                errList.add(e.message ?: "HuijuiShell 异常: ${e::class.simpleName}")
                CommandResult(false, outList, errList)
            }
        }
    }

    /**
     * 检查当前 App 是否拥有 Root 权限
     * 原理不变：执行 id，看返回里有没有 uid=0(root)
     */
    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        val result = exec("id")
        result.isSuccess && result.out.any { it.contains("uid=0") }
    }

    /** 主动关闭常驻会话（App 退出或切换接管状态时可调用） */
    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock { destroyLocked() }
    }

    // ---------------- 内部实现 ----------------

    private fun ensureSession(): Session? {
        session?.let {
            if (it.process.isAlive) return it
            destroyLocked()
        }
        return try {
            val process = ProcessBuilder("su").start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrQueue = LinkedBlockingQueue<String>()

            // stderr 后台常驻线程，避免 pipe 写满把 su 卡死
            thread(isDaemon = true, name = "huijui-shell-stderr") {
                try {
                    val errReader = BufferedReader(InputStreamReader(process.errorStream))
                    while (true) {
                        val line = errReader.readLine() ?: break
                        stderrQueue.offer(line)
                    }
                } catch (_: Exception) {
                    // 进程销毁时会走到这，忽略
                }
            }

            Session(process, writer, reader, stderrQueue).also {
                session = it
                _isAlive.value = true
            }
        } catch (e: Exception) {
            _isAlive.value = false
            null
        }
    }

    private fun writeCommand(s: Session, command: String, id: Long) {
        val sb = StringBuilder(command.length + 64)
        sb.append(command)
        if (!command.endsWith("\n")) sb.append('\n')
        // 注意：\$? 是给 shell 展开的字面 $?，不能被 Kotlin 模板吃掉
        sb.append("echo \"").append(MARKER_PREFIX).append(id).append("__\$?\"\n")
        s.writer.write(sb.toString())
        s.writer.flush()
    }

    /**
     * 用独立线程阻塞读，主线程 join(timeout) 等。
     * 超时就 destroy 会话（关流后读线程抛异常退出），下次 exec 自动重建。
     */
    private fun executeRead(
        s: Session,
        id: Long,
        timeoutMs: Long,
        outList: MutableList<String>
    ): Int {
        val resultRef = AtomicReference<Int?>(null)
        val errorRef = AtomicReference<Throwable?>(null)
        val readerThread = thread(name = "huijui-shell-read-$id") {
            try {
                resultRef.set(readUntilMarker(s, id, outList))
            } catch (t: Throwable) {
                errorRef.set(t)
            }
        }
        readerThread.join(timeoutMs)
        if (readerThread.isAlive) {
            destroyLocked()
            readerThread.join(2_000)
            throw ShellTimeoutException("命令执行超时（${timeoutMs}ms），会话已重建")
        }
        errorRef.get()?.let { throw it }
        return resultRef.get() ?: -1
    }

    private fun readUntilMarker(s: Session, id: Long, outList: MutableList<String>): Int {
        val marker = "$MARKER_PREFIX$id" + "__"
        while (true) {
            val line = s.reader.readLine()
                ?: throw IllegalStateException("su 会话已断开")
            if (line.startsWith(marker)) {
                return line.removePrefix(marker).trim().toIntOrNull() ?: -1
            }
            outList.add(line)
        }
    }

    private fun drainStderr(s: Session, errList: MutableList<String>) {
        while (true) {
            val line = s.stderrQueue.poll() ?: break
            errList.add(line)
        }
    }

    private fun destroyLocked() {
        val s = session ?: return
        session = null
        _isAlive.value = false
        try { s.writer.close() } catch (_: Exception) {}
        try { s.reader.close() } catch (_: Exception) {}
        try { s.process.destroy() } catch (_: Exception) {}
        s.stderrQueue.clear()
    }
}
