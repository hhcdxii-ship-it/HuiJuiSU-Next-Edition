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
        val mode: String, // "HUI", "COMPAT", "NONE", "LOCKED"
        val message: String,
        /** HUI 自己的 socket 是否真的回了包 */
        val huiSocketUp: Boolean = false,
        /**
         * HUI 的内核模块是否真的加载了（/proc/modules 里有 huijui）。
         *
         * 为什么需要这个：守护进程 huijuisud 可能是**上一次安装遗留**还在跑，
         * socket 照样能回 uid=0，但内核模块根本没加载。此时若只看 socket，
         * 就会把一台「其实由 Magisk 提供 Root」的机器判成纯血 HUI。
         * 模块加载才是「HUI 真的在提供 Root」的硬证据。
         */
        val huiModuleLoaded: Boolean = false,
        /** su 是否可用（不代表是 HUI 提供的） */
        val suAvailable: Boolean = false
    )

    data class VerifyReport(
        val isActive: Boolean,
        val detail: String
    )

    suspend fun checkStatus(): EngineStatus = withContext(Dispatchers.IO) {
        // 0. 作弊哨兵触发后的环境锁定：这段时间内引擎一律不工作，
        //    否则「自爆」就只是清空了一次授权，重启 App 就复活了，等于没有惩罚。
        if (EnvironmentPurge.isLocked()) {
            return@withContext EngineStatus(
                false,
                "LOCKED",
                "🔒 环境已锁定（作弊哨兵触发）· ${EnvironmentPurge.remainingLockText()}后解除"
            )
        }

        // 1. HUI 守护进程 socket 能回 uid=0
        if (checkHuiSocket()) {
            val moduleLoaded = isHuiModuleLoaded()
            return@withContext EngineStatus(
                isWorking = true,
                // 只有模块也加载了才算纯血；否则守护进程只是遗留，真正的 Root 另有其人
                mode = if (moduleLoaded) "HUI" else "COMPAT",
                message = if (moduleLoaded) "✅ 纯血引擎运行中"
                else "⚠️ HUI 守护进程在跑，但内核模块未加载（Root 可能来自其他管理器）",
                huiSocketUp = true,
                huiModuleLoaded = moduleLoaded,
                suAvailable = true
            )
        }

        // 2. 兜底：只要 su 真能拿到 uid=0 就放行（但这不等于 HUI 在跑）
        val suOut = suExec("id")
        if (UID_ZERO.containsMatchIn(suOut)) {
            return@withContext EngineStatus(
                isWorking = true,
                mode = "COMPAT",
                message = "✅ 已取得 Root（非 HUI 引擎）",
                huiSocketUp = false,
                suAvailable = true
            )
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
    /** /proc/modules 里有没有 huijui / huijui_phantom。 */
    private suspend fun isHuiModuleLoaded(): Boolean = runCatching {
        val modules = java.io.File("/proc/modules")
        if (modules.canRead()) {
            modules.readText().contains("huijui", ignoreCase = true)
        } else {
            // 普通应用读不到 /proc/modules，退回用 su 查一次
            HuijuiShell.exec("cat /proc/modules 2>/dev/null | grep -i huijui")
                .out.joinToString("").contains("huijui", ignoreCase = true)
        }
    }.getOrDefault(false)

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
