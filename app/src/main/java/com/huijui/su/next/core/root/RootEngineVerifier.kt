package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🩸 纯血接管验证器
 *
 * 设计原则（血泪教训）：
 * 1. 所有函数 suspend —— 底层 HuijuiShell 是长连接协程，同步调用直接编译不过。
 * 2. 不信 ro.* 属性 —— Android init 在 post-fs-data 之后 ro 属性已冻结，
 *    运行时 setprop 更不可能。改用「文件标记 + 进程 + 长连接」三重自证。
 * 3. 不依赖系统 su —— 验证只看我们自己的通道，才算"独立管理器"。
 *
 * 接管判定（三项全绿才算真·工作中）：
 *   A. 专属标记文件存在：/data/adb/huijuisu/.active（daemon 启动时写）
 *   B. 专属进程存活：pidof huijui_ksud 非空
 *   C. 长连接通道通：HuijuiShell 能拉起并执行 id 拿到 uid=0
 */
object RootEngineVerifier {

    const val MARKER_FILE = "/data/adb/huijuisu/.active"
    const val DAEMON_NAME = "huijui_ksud"
    const val VERSION_FILE = "/data/adb/huijuisu/version"

    data class VerifyReport(
        val isActive: Boolean,
        val markerPresent: Boolean,
        val daemonAlive: Boolean,
        val channelRoot: Boolean,
        val version: String?,
        val detail: String
    )

    /** 完整自证，返回详细报告给 UI 状态卡 */
    suspend fun verifyDetailed(): VerifyReport = withContext(Dispatchers.IO) {
        // C：长连接通道是否真的 root
        val channelRoot = runCatching { HuijuiShell.isRooted() }.getOrDefault(false)

        // A：专属标记文件（daemon 真实运行过的证据，比 ro 属性可靠一万倍）
        val markerPresent = runCatching {
            HuijuiShell.exec("test -f $MARKER_FILE && echo yes || echo no")
                .out.joinToString("").trim() == "yes"
        }.getOrDefault(false)

        // B：专属进程
        val daemonAlive = runCatching {
            HuijuiShell.exec("pidof $DAEMON_NAME").out.joinToString("").trim().isNotEmpty()
        }.getOrDefault(false)

        val version = runCatching {
            HuijuiShell.exec("cat $VERSION_FILE 2>/dev/null")
                .out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        // 铁律：标记文件 + 进程 + 通道 三项全绿才算接管
        val active = markerPresent && daemonAlive && channelRoot

        val detail = buildString {
            append("标记文件=").append(if (markerPresent) "✅" else "❌")
            append(" 守护进程=").append(if (daemonAlive) "✅" else "❌")
            append(" Root通道=").append(if (channelRoot) "✅" else "❌")
            if (version != null) append(" 版本=").append(version)
        }

        VerifyReport(active, markerPresent, daemonAlive, channelRoot, version, detail)
    }

    /** 兼容旧调用：只返回布尔 */
    suspend fun verifyHuijuiActive(): Boolean = verifyDetailed().isActive

    /** 检测其他管理器残留（并存检测，不删除，只报告） */
    suspend fun checkOtherManagers(): String? = withContext(Dispatchers.IO) {
        when {
            runCatching { HuijuiShell.exec("ls /data/adb/magisk >/dev/null 2>&1 && echo y").out.joinToString("").contains("y") }.getOrDefault(false) -> "Magisk"
            runCatching { HuijuiShell.exec("ls /data/adb/ksu >/dev/null 2>&1 && echo y").out.joinToString("").contains("y") }.getOrDefault(false) -> "KernelSU"
            runCatching { HuijuiShell.exec("ls /data/adb/ap >/dev/null 2>&1 && echo y").out.joinToString("").contains("y") }.getOrDefault(false) -> "APatch"
            else -> null
        }
    }
}
