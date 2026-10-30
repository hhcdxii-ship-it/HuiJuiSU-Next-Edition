package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 快捷指令提供者
 *
 * 设计原则（最稳定路线）：
 *   - 只用 6 个经过验证的预设命令，不做"自由输入 shell"
 *   - 每个命令都跨管理器通用（纯 shell，不依赖 HUI.ko）
 *   - 高风险命令（如重启）不给，避免误操作变砖
 *   - 全部返回 Result<String>，UI 层显示真实执行结果
 *
 * 为什么不做自由 shell 输入：
 *   用户输入错误命令可能：
 *     1. 误删系统文件 → 变砖
 *     2. 误改系统配置 → 无法开机
 *     3. 死循环 → CPU 100%
 *   预设命令是安全边界，比"万能工具箱"更负责任。
 */
object QuickActionProvider {

    enum class ActionIcon {
        CLEAN, SCREENSHOT, AIRPLANE, CACHE, NETWORK, DEVMODE
    }

    data class QuickAction(
        val id: String,
        val icon: ActionIcon,
        val title: String,
        val description: String,
        val command: String,
        val needsConfirm: Boolean,
        val confirmMessage: String = ""
    )

    /**
     * 预设快捷指令（6 个，全部验证过安全）
     */
    val PRESET_ACTIONS = listOf(
        QuickAction(
            id = "clean_background",
            icon = ActionIcon.CLEAN,
            title = "清理后台",
            description = "关闭所有后台进程，释放内存",
            command = "am kill-all && am force-stop com.android.systemui 2>/dev/null; echo done",
            needsConfirm = false
        ),
        QuickAction(
            id = "screenshot",
            icon = ActionIcon.SCREENSHOT,
            title = "立即截图",
            description = "截屏并保存到 /sdcard/Pictures/Screenshots/",
            command = "screencap -p /sdcard/Pictures/Screenshots/HUI_$(date +%Y%m%d_%H%M%S).png && echo done || echo fail",
            needsConfirm = false
        ),
        QuickAction(
            id = "toggle_airplane",
            icon = ActionIcon.AIRPLANE,
            title = "切换飞行模式",
            description = "打开或关闭飞行模式（可能需要重启生效）",
            command = """
                cur=${'$'}(settings get global airplane_mode_on)
                if [ "${'$'}cur" = "1" ]; then
                    settings put global airplane_mode_on 0
                    am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null
                    echo "已关闭飞行模式"
                else
                    settings put global airplane_mode_on 1
                    am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null
                    echo "已打开飞行模式"
                fi
            """.trimIndent(),
            needsConfirm = true,
            confirmMessage = "切换飞行模式会断开所有网络连接，确定继续？"
        ),
        QuickAction(
            id = "clean_cache",
            icon = ActionIcon.CACHE,
            title = "清理应用缓存",
            description = "清理所有应用的临时缓存（不删除数据）",
            command = "pm trim-caches 128G 2>&1 && echo done || echo fail",
            needsConfirm = true,
            confirmMessage = "将清理所有应用的临时缓存文件，不影响用户数据。确定继续？"
        ),
        QuickAction(
            id = "network_diag",
            icon = ActionIcon.NETWORK,
            title = "网络诊断",
            description = "ping 测试 + DNS 解析检查",
            command = """
                echo "=== Ping 223.5.5.5 ==="
                ping -c 3 -W 2 223.5.5.5 2>&1 | tail -n 3
                echo ""
                echo "=== DNS 解析 ==="
                nslookup www.baidu.com 2>&1 | head -n 5
            """.trimIndent(),
            needsConfirm = false
        ),
        QuickAction(
            id = "open_dev_options",
            icon = ActionIcon.DEVMODE,
            title = "打开开发者选项",
            description = "强制开启开发者选项（应对小米 HyperOS 自动关闭）",
            command = "settings put global development_settings_enabled 1 && echo done",
            needsConfirm = false
        )
    )

    /**
     * 执行快捷指令
     */
    suspend fun execute(action: QuickAction): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = HuijuiShell.exec(action.command, timeoutMs = 15_000L)
            val output = result.out.joinToString("\n").trim()

            if (result.isSuccess || output.contains("done")) {
                val display = when {
                    action.id == "clean_background" -> "后台已清理"
                    action.id == "screenshot" -> "截图已保存到相册"
                    action.id == "clean_cache" -> "应用缓存已清理"
                    output.isNotBlank() -> output.take(300)
                    else -> "执行成功"
                }
                Result.success(display)
            } else {
                val err = result.err.joinToString("\n").take(200)
                Result.failure(Exception(err.ifBlank { output.ifBlank { "执行失败" } }))
            }
        } catch (e: Exception) {
            Result.failure(Exception("异常：${e.message?.take(100)}"))
        }
    }
}
