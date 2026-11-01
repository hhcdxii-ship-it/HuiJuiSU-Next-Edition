package com.huijui.sunext.edition.core.ui.language

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlin.math.abs

private val zakoDict = mapOf(

    // 🚀 专属引导页（Onboarding）向导语气
    "欢迎回家，机友" to "杂鱼，滚回来啦？",
    "从今天起，你的设备真正属于你" to "从今天起，你的破铜烂铁归本小姐管了！",
    "HuiJuiSU Next 不只是一个 Root 管理器，它是一个有温度、有生命的控制台。接下来 13 步，带你认识它、配置它、拥有它。" to "才不是什么冷冰冰的 Root 管理器呢！本小姐是有温度、有生命的控制台！接下来 13 步，老老实实跟着本小姐认清楚它，懂不懂？",
    
    "纯血内核，自己写的那种" to "纯血内核，本小姐亲手写的哦",
    "脱离 KSU / Magisk，一行行堆出来的 HUI.ko" to "才不是什么 KSU 套皮呢，一行一行敲出来的 HUI.ko 啦！",
    "自研 C 内核模块 + Hsud 守护进程 + 一键热接管。没有套皮，没有借用，每一行都是自己干的。" to "自研 C 内核模块 + Hsud 守护进程 + 一键热接管！没有借用，没有抄袭，全是我自己干的！笨蛋杂鱼，听懂了没？",
    
    "模块热挂载，零重启" to "插插模块，不用重启哦♡",
    "刷完就能用，不用再等那一分钟" to "塞进去马上就能用，才不会让你苦等呢！",
    "本地 ZIP 直接热挂载，配合 HUI 内核接管，全程零重启体验。" to "本地 ZIP 直接热挂载！配合 HUI 内核无缝接管，全程零重启！这可是本小姐的绝活，学着点！",
    
    "每个应用，你说得算" to "想给哪个杂鱼 App 权限，你说了算",
    "精细到单个应用的 Root 权限" to "连哪个 App 敢要 Root 都要本小姐亲自审批！",
    "读取真实应用图标，开关一拨，授权即刻生效。谁该有、谁不该有，你心里有数。" to "读取真实的破图标，开关一拨，立刻生效！谁配拥有权限，谁不配，你心里最好有点数，杂鱼！",
    
    "镜像修补，一键生成" to "修破镜像？一键就搞定了啦",
    "init_boot / boot 重打包，电脑刷入" to "init_boot 还是 boot？全自动重打包，连电脑都不用找！",
    "A13+ 强制走 init_boot，解包、替换、注入 rc、重打包全自动。防呆拦截已在底层生效。" to "只要是 A13 以上，强制走 init_boot！解包、替换、注入、重打包全自动！而且本小姐加了防呆拦截，笨蛋杂鱼绝对变不了砖！",
    
    "环境检测，硬核到底" to "环境检测？看本小姐扒了它的底裤！",
    "KSU / Magisk / APatch / 越狱，一个都跑不掉" to "管你是 KSU、Magisk 还是 APatch，就算越狱了，也休想逃出本小姐的手掌心！",
    "RootEnvironmentDetector + RootEngineVerifier 双保险，检查专属进程、属性、/dev/huijui 设备节点。" to "双重保险！查进程、查属性、查 /dev/huijui 节点，哪个野路子管理器能骗过我的眼睛？",
    
    "挑一个主题色" to "挑个颜色涂一涂吧，笨蛋",
    "四种性格，你的偏好" to "四种性格，随便你挑，哼",
    "可在设置里随时更换" to "反正你以后肯定还会改的。",
    
    "材质风格" to "想用什么材质？",
    "卡片长什么样，你说了算" to "卡片长什么样，本小姐勉强听听你的意见。",
    "毛玻璃 / 实心 / 无材质三档" to "就这三档：毛玻璃、实心、或者啥都别用！",
    
    "对话风格" to "喜欢被本小姐怎么骂？",
    "正常模式 还是 杂鱼模式？" to "正经模式，还是想被本小姐当杂鱼骂？",
    "随时可以在设置里改" to "反正你随时都会反悔，去设置里改吧。",
    
    "首次进入，请放行 Root" to "喂！快点给本小姐放行 Root！",
    "只有授权后才能解锁全部功能" to "没有权限，你什么都干不了，懂吗杂鱼？",
    "如果没授权，底部「超管」入口会变灰。点击能量球刷新状态时，系统弹窗直接允许即可。" to "如果没给权限，底部的「大小姐」入口就会灰掉！到时候点能量球刷新，弹窗出来赶紧点允许！别逼本小姐动手！",
    
    "几个小技巧" to "教你点小窍门",
    "老司机都这么用" to "笨蛋杂鱼学着点！",
    "准备好了吗？" to "准备好了没啊，杂鱼？",
    "接下来，交给 HUI" to "剩下的，就全部交给本小姐吧！",
    "所有配置已保存，点击右下角进入你的纯血控制台。" to "都给你保存好了！戳右下角，赶紧给本小姐进控制台！"
,

    "主页" to "杂鱼的狗窝", "工具" to "玩具箱", "超管" to "大小姐", "模块" to "插模块", "设置" to "调教",
    "🚀 一键智能提取并刷入" to "把破镜像塞进去！", "工作中 (HUI)..." to "本小姐在干活啦...",
    "HuiJuiSU Next - 引擎重载" to "本小姐的底层重载", "纯血独立 / 全自动 LKM 注入" to "纯血杂鱼，自动塞入内核",
    // 🚀 补充的状态和 UI 词条
    "未工作" to "本小姐在偷懒啦",
    "未工作 (无 Root)" to "没权限？杂鱼赶紧去授权啊！",
    "工作中 (KSU 兼容)" to "本小姐借了KSU的壳在干活啦...",
    "工作中 (KSU)" to "本小姐借了KSU的壳在干活啦...",
    "工作中 (Magisk)" to "本小姐借了面具在干活啦...",
    "工作中 (APatch)" to "本小姐借了APatch在干活啦...",
    "工作中 (HUI)" to "本小姐在纯血干活啦！",
    "Root 状态" to "大小姐的权限检查",
    "系统与设备信息" to "看本小姐扒光这台机器",
    "底层重载" to "重新启动底层"
)

fun zakoString(text: String): String {
    if (AppSettings.currentLanguage != "ZAKO") return text
    zakoDict[text]?.let { return it }
    
    if (text.contains("[模块] 匹配内置纯血内核: ")) {
        return text.replace("[模块] 匹配内置纯血内核: ", "本小姐匹配到了这只内核: ")
    }
    if (text.contains("[读取] 镜像读取成功，大小: ")) {
        return text.replace("[读取] 镜像读取成功，大小: ", "偷到了底层镜像，才").replace("MB，准备塞入杂鱼内核...", "MB捏~")
    }
    if (text.contains("执行异常: ")) {
        return text.replace("执行异常: ", "杂鱼你干了什么破事: ")
    }

    // 🛑 智能兜底已废除：没在字典里的字，保持原样，不打乱 UI 语境
    return text
}

@Composable
fun ZakoText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = zakoString(text), modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, style = style
    )
}
