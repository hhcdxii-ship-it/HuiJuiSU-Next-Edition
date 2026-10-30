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
    // 🚀 底栏
    "主页" to "杂鱼的狗窝",
    "工具" to "玩具箱",
    "超管" to "大小姐",
    "模块" to "插模块",
    "设置" to "调教",

    // 🚀 主页
    "SYSTEM TIME" to "SYSTEM TIME",
    "ROOT STATUS" to "ROOT 状态",
    "未工作" to "连工作都不会的废柴~",
    "工作中" to "本小姐在干活啦",
    "独立" to "本小姐独立啦！",
    "兼容" to "寄人篱下捏~",
    "未获取权限" to "没权限的废物",
    "底层重载" to "又要重载底层？真麻烦呢",
    "刷入镜像" to "把破镜像塞进去",
    "模块挂载" to "插插模块♡",
    "接管底层" to "杂鱼夺舍！",
    "系统与设备信息" to "看什么看，杂鱼的小破机",
    "设备型号" to "杂鱼的小破机",
    "系统版本" to "烂橘子系统",
    "内核版本" to "底层杂鱼",
    "系统指纹" to "没用的指纹",
    "未知" to "啥都不知道的笨蛋",

    // 🚀 工具页
    "调优与工具" to "本小姐的调教工具",
    "日常管理" to "杂鱼的日常垃圾",
    "系统性能" to "累死你CPU",
    "设备与测试" to "你的破铜烂铁",
    "快捷指令" to "快速调教",
    "App 管理" to "杂鱼破App",
    "Root 审计" to "查你底裤",
    "权限审计" to "看多贪婪",
    "配置方舟" to "杂鱼避难所",
    "隐藏防御" to "杂鱼隐身衣",
    "CPU 调度" to "驯服CPU",
    "系统魔改" to "烂系统魔改",
    "分区备份" to "留个种",
    "终端命令" to "敲键盘",
    "硬件信息" to "看破铜烂铁",
    "传感器" to "别乱摸！",
    "屏幕测试" to "看瞎你",

    // 🚀 设置页
    "配置与个性化" to "调教与个性化",
    "主题设置" to "换件衣服",
    "自定义材质、壁纸与动画效果" to "随便换，反正你品味也不行",
    "语言设置" to "学狗叫",
    "支持简繁、英日韩俄以及杂鱼语" to "什么鸟语都有，包括雌小鬼语",
    "HUI 独有特性" to "本小姐的独有魅力",
    "Omni-Root 引擎" to "万能杂鱼引擎",
    "兼容并接管 KSU/Magisk 底层生态" to "把底层垃圾都踩在脚下",
    "3 级防格机拦截" to "三级防变砖保护",
    "白名单分区过滤 + GKI 校验" to "不许乱动我的系统文件",
    "无感热备份" to "偷偷给你留个种",
    "刷机前自动 dd 备份，秒级回滚" to "搞坏了也能马上恢复，笨蛋",
    "原生 WebUI 沙盒" to "网页控制台",
    "支持 Magisk/KSU 模块 HTML 控制台" to "让那些模块自己动",
    "重置与危险操作" to "毁灭与作死",
    "恢复出厂设置" to "想逃避现实？",
    "清空所有主题、壁纸、引导状态（保留 Root 授权）" to "要把你的痕迹全删了？杂鱼舍得吗？",
    "关于" to "关于本小姐",

    // 🚀 模块页
    "模块拓展" to "插插模块",
    "本地已装" to "家里塞的",
    "在线仓库" to "外面的垃圾",
    "一个模块都没有" to "啥都没装，杂鱼~",
    "没读到模块，杂鱼真可怜~" to "没读到模块，真可怜",
    "仓库里空空如也" to "垃圾堆都空了哦~",
    "重新拉取" to "再拉一次",
    "网络断了，杂鱼连上网都不会吗？" to "断网啦废物！",

    // 🚀 超管页
    "超级用户" to "权限大小姐",
    "当前 Root 管理器" to "目前谁在管你",
    "正在扫你的垃圾应用…" to "正在扫垃圾…",
    "搜索应用名或包名" to "搜吧，反正你也找不到",

    // 🚀 其他零星词（扫描出的156个里挑出的高频词）
    "返回" to "想逃？",
    "刷新" to "再戳一下",
    "取消" to "怂了",
    "确认" to "快点确认啦，笨蛋",
    "未知管理器" to "不知名野鸡",
    "已授权" to "算你识相",
    "未授予" to "别想了杂鱼",
    "已拒绝" to "滚吧",
    "已冻结" to "冻住了",
    "已卸载" to "滚粗了",
    "加载中" to "别催啦，杂鱼~",
    "准备中" to "急什么，杂鱼",
    "检测中" to "让我看看你的底细~",
    "成功" to "居然成功了？走狗屎运",
    "失败" to "果然失败了，杂鱼就是杂鱼",
    "确定继续？" to "真的要继续吗笨蛋？",
    "确认执行" to "快点执行啦！",

    // 🚀 V4 补充：主界面/引导/设置/按钮高频词（第一批 80 条）
    // —— 主页/引导 ——
    "万能工具箱" to "万能杂鱼箱",
    "主页 / 工具 / 超管 / 设置 四入口" to "四个破入口，随便戳",
    "两种人生，一键切换" to "两种人生，杂鱼选一个",
    "二游风 · 能量球主页" to "二游风 · 杂鱼能量球",
    "二游风的启动器，老板风的专业台" to "花里胡哨的启动器，老板风的专业台",
    "右上角一点，能量球和极简列表随时切换。想热血就热血，想冷静就冷静。" to "戳右上角，破能量球和极简列表随便切。热血还是冷静，杂鱼你定。",
    "右上角点击 GridView / ViewList 图标" to "戳右上角那个破图标",
    "启动器模式" to "启动器模式",
    "老板风 · 极简列表" to "老板风 · 极简列表",
    "正常模式" to "正经模式",
    "正常模式 还是 杂鱼模式？" to "正经模式还是杂鱼模式？",
    "四种性格，你的偏好" to "四种破性格，杂鱼你挑",
    // —— 语言/主题 ——
    "人类语言" to "人类语言",
    "日本語" to "日本語",
    "简体中文" to "简体中文",
    "繁體中文" to "繁體中文",
    "言語を選択" to "言語を選択",
    "選擇你的語言" to "選擇你的語言",
    "言語設定" to "言語設定",
    "語言設定" to "語言設定",
    "設定" to "設定",
    "对话风格" to "对话风格",
    "材质风格" to "材质风格",
    "毛玻璃" to "毛玻璃",
    "实心" to "实心",
    "极简无材质" to "极简无材质",
    "毛玻璃 / 实心 / 无材质三档" to "三档破材质，随便挑",
    "卡片圆角" to "卡片圆角",
    "全局动画速度" to "全局动画速度",
    "过渡动画" to "过渡动画",
    "平滑过渡 (Smooth)" to "平滑过渡",
    "弹簧物理回弹 (Spring)" to "弹簧回弹",
    "线性过渡，干练不拖沓" to "线性过渡，干练不拖沓",
    "无动画" to "无动画，卡死你",
    "极客绿" to "极客绿",
    "赛博蓝" to "赛博蓝",
    "烈焰红" to "烈焰红",
    "纯白" to "纯白",
    "强调色" to "强调色",
    "挑一个主题色" to "挑一个破颜色",
    "挑一个吧，杂鱼" to "挑一个吧，杂鱼",
    // —— 通用按钮/操作 ——
    "下一步" to "赶紧下一步",
    "跳过" to "跳过",
    "完成" to "搞定了",
    "继续" to "继续",
    "关闭" to "关掉",
    "删除" to "删了",
    "移除" to "扔掉",
    "安装" to "塞进去",
    "卸载" to "滚粗",
    "冻结" to "冻住",
    "解冻" to "解冻",
    "停止" to "停下",
    "启动" to "启动",
    "不支持" to "不支持，杂鱼别想了",
    "操作失败" to "搞砸了",
    "操作成功" to "居然搞定了",
    // —— 确认类 ——
    "确认刷入" to "确认塞进去？",
    "确认恢复" to "确认恢复？",
    "确认还原" to "确认还原？",
    "确认重置" to "确认重置？",
    "确认切换" to "确认切换？",
    "确认启用" to "确认启用？",
    // —— 状态/提示 ——
    "暂无 Root 请求" to "没有杂鱼来求本小姐",
    "暂无 Root 请求记录" to "一个求本小姐的都没有",
    "暂无已挂载模块" to "啥模块都没挂，杂鱼~",
    "还没有备份" to "连个种都没留",
    "还没有方舟快照" to "连个避难所都没建",
    "后台已清理" to "后台垃圾清干净了",
    "应用缓存已清理" to "破缓存清掉了",
    "同步完成" to "同步完事了",
    "同步底层" to "同步底层",
    "已更新底层授权" to "底层授权更新了",
    "已复制全部信息到剪贴板" to "全抄到剪贴板了",
    "已恢复出厂设置" to "逃避现实成功了？",
    // —— 安全/反作弊 ——
    "危险的内存修改/注入工具" to "危险的内存修改器，别碰",
    "联机脚本注入/执行器，严重破坏游戏平衡" to "联机外挂脚本，杂鱼滚粗",
    "检测到联机作弊特征" to "抓到杂鱼开挂了"
)

fun zakoString(text: String): String {
    if (AppSettings.currentLanguage != "ZAKO") return text

    // 1. 精确匹配
    zakoDict[text]?.let { return it }

    // 2. 拦截 "工作中 (XXX 兼容)" 等长句
    val workingMatch = Regex("""工作中\s*\((.*?)\)""").find(text)
    if (workingMatch != null) {
        val inner = workingMatch.groupValues[1]
        if (inner.contains("Unknown") || inner.contains("未知")) {
            return "替不知道哪来的野路子打工中"
        }
        return "替 $inner 打工中"
    }
    if (text.contains("已授权") && text.contains("个应用") && text.contains("/")) {
        return text.replace("个应用", "个杂鱼")
    }

    // 3. 🚀 防重复智能兜底（基于 Hash 算死，同一句话永远同一个结果，不同句子结果不同）
    if (text.matches(Regex(""".*[\u4e00-\u9fa5].*"""))) {
        val hash = abs(text.hashCode())
        val suffixes = listOf(
            " 懂不懂啊？",
            " 真是拿你没办法~",
            " 杂鱼就是杂鱼，连这都不会吗？",
            " 哼，本小姐才不想管你呢！",
            " 连这个都没见过，真可怜~",
            " 别拿这种破事来烦本小姐！",
            " 这点小事都做不好吗？"
        )
        return "$text" + suffixes[hash % suffixes.size]
    }

    return text
}

@Composable
fun ZakoText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = zakoString(text), modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}
