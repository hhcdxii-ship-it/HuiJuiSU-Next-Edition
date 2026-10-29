package com.huijui.su.next.core.ui.language

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

enum class LanguageMode(val code: String, val displayName: String) {
    ZH_CN("zh_cn", "简体中文"),
    ZH_TW("zh_tw", "繁體中文"),
    EN("en", "English"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    RU("ru", "Русский"),
    ZAKO("zako", "杂鱼模式♡")
}

val LocalLanguageMode = staticCompositionLocalOf { LanguageMode.ZH_CN }

object HuiStrings {
    // 核心字典：以简体中文为 Key，映射到其他语言
    private val dict = mapOf(
        "主页" to mapOf(
            LanguageMode.ZH_CN to "主页",
            LanguageMode.ZH_TW to "主頁",
            LanguageMode.EN to "Home",
            LanguageMode.JA to "ホーム",
            LanguageMode.KO to "홈",
            LanguageMode.RU to "Главная",
            LanguageMode.ZAKO to "杂鱼主页"
        ),
        "工具" to mapOf(
            LanguageMode.ZH_CN to "工具",
            LanguageMode.ZH_TW to "工具",
            LanguageMode.EN to "Tools",
            LanguageMode.JA to "ツール",
            LanguageMode.KO to "도구",
            LanguageMode.RU to "Инструменты",
            LanguageMode.ZAKO to "杂鱼工具"
        ),
        "超管" to mapOf(
            LanguageMode.ZH_CN to "超管",
            LanguageMode.ZH_TW to "超管",
            LanguageMode.EN to "Superuser",
            LanguageMode.JA to "スーパーユーザー",
            LanguageMode.KO to "슈퍼유저",
            LanguageMode.RU to "Суперпользователь",
            LanguageMode.ZAKO to "杂鱼超管"
        ),
        "设置" to mapOf(
            LanguageMode.ZH_CN to "设置",
            LanguageMode.ZH_TW to "設定",
            LanguageMode.EN to "Settings",
            LanguageMode.JA to "設定",
            LanguageMode.KO to "설정",
            LanguageMode.RU to "Настройки",
            LanguageMode.ZAKO to "杂鱼的设置"
        ),
        "语言设置" to mapOf(
            LanguageMode.ZH_CN to "语言设置",
            LanguageMode.ZH_TW to "語言設定",
            LanguageMode.EN to "Language",
            LanguageMode.JA to "言語設定",
            LanguageMode.KO to "언어 설정",
            LanguageMode.RU to "Язык",
            LanguageMode.ZAKO to "杂鱼也会外语？"
        ),
        "选择你的语言" to mapOf(
            LanguageMode.ZH_CN to "选择你的语言",
            LanguageMode.ZH_TW to "選擇你的語言",
            LanguageMode.EN to "Select Language",
            LanguageMode.JA to "言語を選択",
            LanguageMode.KO to "언어 선택",
            LanguageMode.RU to "Выберите язык",
            LanguageMode.ZAKO to "挑一个吧，杂鱼"
        ),
        "深色模式" to mapOf(
            LanguageMode.ZH_CN to "深色模式",
            LanguageMode.ZH_TW to "深色模式",
            LanguageMode.EN to "Dark Mode",
            LanguageMode.JA to "ダークモード",
            LanguageMode.KO to "다크 모드",
            LanguageMode.RU to "Темная тема",
            LanguageMode.ZAKO to "太黑了吧，杂鱼"
        ),
        "环境隔离防御" to mapOf(
            LanguageMode.ZH_CN to "环境隔离防御",
            LanguageMode.ZH_TW to "環境隔離防禦",
            LanguageMode.EN to "Environment Isolation",
            LanguageMode.JA to "環境分離防御",
            LanguageMode.KO to "환경 격리 방어",
            LanguageMode.RU to "Изоляция среды",
            LanguageMode.ZAKO to "杂鱼的隐身衣♡"
        ),
        "配置方舟" to mapOf(
            LanguageMode.ZH_CN to "配置方舟",
            LanguageMode.ZH_TW to "配置方舟",
            LanguageMode.EN to "Config Ark",
            LanguageMode.JA to "コンフィグアーク",
            LanguageMode.KO to "구성 방주",
            LanguageMode.RU to "Ковчег конфигурации",
            LanguageMode.ZAKO to "杂鱼方舟♡"
        ),
        "纯净守护者 (反作弊)" to mapOf(
            LanguageMode.ZH_CN to "纯净守护者 (反作弊)",
            LanguageMode.ZH_TW to "純淨守護者 (反作弊)",
            LanguageMode.EN to "Pure Guardian (Anti-Cheat)",
            LanguageMode.JA to "ピュアガーディアン (アンチチート)",
            LanguageMode.KO to "퓨어 가디언 (안티 치트)",
            LanguageMode.RU to "Чистый страж (Античит)",
            LanguageMode.ZAKO to "杂鱼克星♡ 外挂滚粗"
        ),
        "返回" to mapOf(
            LanguageMode.ZH_CN to "返回",
            LanguageMode.ZH_TW to "返回",
            LanguageMode.EN to "Back",
            LanguageMode.JA to "戻る",
            LanguageMode.KO to "뒤로",
            LanguageMode.RU to "Назад",
            LanguageMode.ZAKO to "回去啦，杂鱼"
        )
        // 你可以在这里继续添加更多词条
    )

    fun get(original: String, mode: LanguageMode): String {
        val translations = dict[original]
        return translations?.get(mode) ?: original // 如果没有翻译，回退到原文
    }
}

@Composable
fun ZakoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
    style: TextStyle = TextStyle.Default
) {
    val mode = LocalLanguageMode.current
    Text(
        text = HuiStrings.get(text, mode),
        modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}
