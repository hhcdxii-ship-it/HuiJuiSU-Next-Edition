package com.huijui.su.next.core.ui.language
import com.huijui.su.next.core.ui.language.ZakoText

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

enum class LanguageMode { NORMAL, ZAKO }

val LocalLanguageMode = staticCompositionLocalOf { LanguageMode.NORMAL }

object HuiStrings {
    private val zakoMap = mapOf(
        "HuiJuiSU" to "杂鱼~杂鱼~",
        "Next Edition · 纯血控制台" to "本小姐勉为其难在控制呢♡",
        "未获取权限" to "连Root都没有吗？杂鱼~",
        "工作中" to "本小姐勉强在工作呢♡",
        "系统指纹" to "杂鱼的系统指纹...看了也白看吧？",
        "版本信息" to "记好了，杂鱼版本哦",
        "设备" to "杂鱼用的设备",
        "内核版本" to "反正杂鱼也看不懂内核吧？",
        "Android 版本" to "Android 版本♡ 不会是老古董吧？",
        "状态信息" to "杂鱼的状态...没什么好看的啦",
        "SELinux 状态" to "SELinux 状态，杂鱼能理解吗？",
        "活跃管理器" to "杂鱼管理器",
        "钩子类型" to "底层魔法~",
        "快捷操作" to "快点选一个啦，杂鱼",
        "纯血镜像修补" to "镜像修补...杂鱼自己来试试看？",
        "生成可电脑刷入的 init_boot/boot 镜像" to "生成镜像什么的，杂鱼会吗？",
        "⚡ 极速热接管" to "⚡ 极速热接管♡ 还要本小姐帮忙？",
        "点击拉起 HUI 内核接管底层" to "点一下嘛，杂鱼~ 又不会弄坏手机",
        "HUI 内核控制台" to "杂鱼也想看内核日志？看得懂吗~",
        "查看底层日志与性能调度" to "底层日志...反正杂鱼看了也白看",
        "设置" to "杂鱼的设置...随便调调就好",
        "配置" to "配置一下啦",
        "个性化" to "杂鱼也想变好看？",
        "安全与隐私" to "怕被看到吗，杂鱼？",
        "支持与反馈" to "有问题就告诉我啦~",
        "关于" to "关于本小姐",
        "模块管理" to "杂鱼模块...装再多也还是杂鱼",
        "底层热挂载 · 零重启体验" to "热挂载♡ 杂鱼连重启都不会吗？",
        "暂无已加载模块" to "诶~一个模块都没有？果然是杂鱼呢",
        "选择本地 ZIP 模块安装" to "选个ZIP来~杂鱼可别选错了哦",
        "超级用户" to "杂鱼用户...才不是超级用户呢",
        "应用 Root 权限管理" to "Root权限管理♡ 杂鱼也配管权限？",
        "主页" to "杂鱼主页",
        "超管" to "杂鱼超管",
        "模块" to "杂鱼模块",
        "工具" to "杂鱼工具",
        "主题设置" to "杂鱼也要换主题？",
        "自定义材质、壁纸与动画效果" to "换什么都是杂鱼啦~"
    )

    fun get(original: String, mode: LanguageMode): String {
        return if (mode == LanguageMode.ZAKO) zakoMap[original] ?: original
        else original
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
