package com.huijui.su.next.core.root

/**
 * 🎨 刷入 / 安装日志翻译器
 *
 * 把底层原始日志翻译成"人类语言"，过滤掉用户不需要看的工程输出：
 *   - 工程日志（dd / mke2fs / 十六进制地址）→ 丢弃
 *   - 关键阶段（备份 / 刷入 / 校验）→ 翻译成中文
 *   - 音量键请求（Press Vol+ / 音量+）→ 转为 VolumeRequest（触发 App 是/否面板）
 *   - 成功/失败 → 翻译成 ✅ / ❌
 *
 * 使用约定：
 *   - translate() 返回 TranslatedLine?，null 表示丢弃该行
 *   - VolumeRequest 是特殊事件，UI 层收到后弹 Dialog，用户点击再发 keyevent 24/25
 */
object FlashLogTranslator {

    sealed class TranslatedLine {
        /** 普通文本（显示在"当前阶段"区域，不滚动） */
        data class Text(val text: String) : TranslatedLine()

        /** 进度提示（如"已解压 1.2MB"） */
        data class Progress(val text: String) : TranslatedLine()

        /** 音量键请求 → UI 弹"是/否"面板 */
        data class VolumeRequest(val message: String) : TranslatedLine()

        /** 成功/失败终态 */
        data class Result(val success: Boolean, val message: String) : TranslatedLine()
    }

    // ===== 关键词表 =====
    // 音量键请求关键词（多语言兼容）
    private val VOLUME_REQUEST_PATTERNS = listOf(
        Regex("(?i)press\\s+vol(?:ume)?\\s*\\+"),
        Regex("(?i)press\\s+vol(?:ume)?\\s*-"),
        Regex("(?i)vol(?:ume)?\\s*\\+\\s*(?:to|for)\\s*(?:confirm|continue|yes)"),
        Regex("(?i)vol(?:ume)?\\s*-\\s*(?:to|for)\\s*(?:cancel|abort|no)"),
        Regex("(?i)volume\\s+(?:up|down)"),
        Regex("(?i)音量\\s*[+＋]"),
        Regex("(?i)音量\\s*[-－]"),
        Regex("(?i)按.{0,4}音量"),
        Regex("(?i)press\\s+any\\s+key\\s+to\\s+continue")
    )

    // 成功关键词
    private val SUCCESS_PATTERNS = listOf(
        Regex("(?i)^\\s*(?:success|successful|done|completed?)\\s*$"),
        Regex("(?i)install(?:ed|ation)\\s+success"),
        Regex("(?i)flashed\\s+success"),
        Regex("(?i)安装成功"),
        Regex("(?i)刷入成功")
    )

    // 失败关键词
    private val FAILURE_PATTERNS = listOf(
        Regex("(?i)^\\s*(?:fail(?:ed|ure)?|error|aborted?)\\s*$"),
        Regex("(?i)install(?:ed|ation)\\s+fail"),
        Regex("(?i)flashed\\s+fail"),
        Regex("(?i)安装失败"),
        Regex("(?i)刷入失败")
    )

    // 工程日志关键词（这些行直接丢弃）
    private val NOISE_PATTERNS = listOf(
        Regex("^\\s*\\d+\\+\\d+ records (?:in|out)"),
        Regex("^\\s*\\d+ bytes? \\(.*\\) copied"),
        Regex("(?i)^mke2fs"),
        Regex("(?i)^e2fsck"),
        Regex("(?i)^debugfs"),
        Regex("^\\s*0x[0-9a-fA-F]+"),
        Regex("^\\s*$"),  // 空行
        Regex("^\\s*[.]+\\s*$")  // 纯点点点进度条
    )

    // 备份 / 刷入 / 校验阶段（正常日志翻译）
    private val STAGE_TRANSLATIONS = listOf(
        Regex("(?i)backing\\s+up") to "正在备份原分区…",
        Regex("(?i)writing\\s+to") to "正在写入分区…",
        Regex("(?i)verifying") to "正在校验…",
        Regex("(?i)unpack(?:ing)?\\s+zip") to "正在解压模块…",
        Regex("(?i)extract(?:ing)?") to "正在解压…",
        Regex("(?i)copying\\s+files?") to "正在复制文件…",
        Regex("(?i)setting\\s+permissions") to "正在设置权限…",
        Regex("(?i)mounting") to "正在挂载…",
        Regex("(?i)unmounting") to "正在卸载…"
    )

    /**
     * 翻译单行
     * @param raw 原始日志行
     * @return 翻译结果，null 表示丢弃
     */
    fun translate(raw: String): TranslatedLine? {
        val line = raw.trimEnd()

        // 1. 先过滤工程噪音
        if (NOISE_PATTERNS.any { it.containsMatchIn(line) }) return null

        // 2. 音量键请求（优先级最高，因为要弹面板）
        if (VOLUME_REQUEST_PATTERNS.any { it.containsMatchIn(line) }) {
            return TranslatedLine.VolumeRequest(
                message = "模块安装脚本请求确认，是否继续？"
            )
        }

        // 3. 成功 / 失败
        if (SUCCESS_PATTERNS.any { it.containsMatchIn(line) }) {
            return TranslatedLine.Result(true, "操作成功")
        }
        if (FAILURE_PATTERNS.any { it.containsMatchIn(line) }) {
            return TranslatedLine.Result(false, "操作失败")
        }

        // 4. 阶段翻译
        for ((pattern, translation) in STAGE_TRANSLATIONS) {
            if (pattern.containsMatchIn(line)) {
                return TranslatedLine.Text(translation)
            }
        }

        // 5. 进度提示（数字 + MB/KB/GB）
        val progressMatch = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(KB|MB|GB|B)").find(line)
        if (progressMatch != null) {
            return TranslatedLine.Progress("已处理 ${progressMatch.value}")
        }

        // 6. 其他 → 丢弃（默认不暴露原始日志）
        return null
    }

    /**
     * 获取音量键确认的 keyevent 码
     */
    fun volumeUpKeyCode(): Int = 24
    fun volumeDownKeyCode(): Int = 25
}
