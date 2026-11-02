package com.huijui.sunext.edition.core.ui.language

import androidx.compose.runtime.staticCompositionLocalOf
import com.huijui.sunext.edition.core.utils.AppSettings

enum class LanguageMode(val code: String, val displayName: String, val nativeName: String) {
    ZH_CN("zh_cn", "简体中文", "简体中文"),
    ZH_TW("zh_tw", "繁體中文", "繁體中文"),
    EN("en", "English", "English"),
    JA("ja", "日本語", "日本語"),
    KO("ko", "한국어", "한국어"),
    RU("ru", "Русский", "Русский"),
    ZAKO("zako", "杂鱼模式♡", "杂鱼模式♡")
}

val LocalLanguageMode = staticCompositionLocalOf { LanguageMode.ZH_CN }

/**
 * 全局文案层。
 *
 * 重构前这里是一张**从来没人调用**的死表：`HuiStrings.get()` 全项目零引用，
 * `LocalLanguageMode` 提供了却没人读，实际只有 `zakoString()` 在跑，
 * 而它只认 `currentLanguage == "ZAKO"` 这一个字面量。
 * 也就是说「7 种语言」在界面上只表现为「简中」和「杂鱼」两档，
 * 而且选择器里除这两档之外全部被标成「施工中」不可点。
 *
 * 现在统一成一条管线：
 *
 *     ZakoText(text) → HuiStrings.tr(text, 当前模式)
 *
 * 查找顺序：
 *   1. 该语言的显式词条
 *   2. 语言缺词 → 回落英文（对非中文用户，英文比中文可读）
 *   3. ZAKO 缺词 → 走 [zakoize] 语气转换，保证 100% 覆盖而不是半中文
 *   4. 仍无 → 返回原文
 */
object HuiStrings {

    private val zhTw = mapOf(
        "主页" to "主頁", "工具" to "工具", "超管" to "超管", "模块" to "模組", "设置" to "設定",
        "语言设置" to "語言設定", "选择你的语言" to "選擇你的語言",
        "授权" to "授權", "未工作" to "未工作", "检测中..." to "偵測中…",
        "刷入镜像" to "刷入鏡像", "底层重载" to "底層重載", "模块挂载" to "模組掛載",
        "内核接管" to "內核接管", "接管底层" to "接管底層",
        "系统与设备信息" to "系統與裝置資訊", "设备型号" to "裝置型號", "系统版本" to "系統版本",
        "内核版本" to "內核版本", "系统指纹" to "系統指紋",
        "调优与工具" to "調校與工具", "日常管理" to "日常管理", "系统性能" to "系統效能",
        "进阶玩法" to "進階玩法", "设备与测试" to "裝置與測試",
        "快捷指令" to "快捷指令", "App 管理" to "App 管理", "Root 审计" to "Root 稽核",
        "权限审计" to "權限稽核", "CPU 调度" to "CPU 調度", "系统魔改" to "系統魔改",
        "分区备份" to "分區備份", "终端命令" to "終端指令", "内核模块" to "內核模組",
        "方舟快照" to "方舟快照", "隐藏与反检测" to "隱藏與反偵測",
        "硬件信息" to "硬體資訊", "传感器" to "感測器", "屏幕测试" to "螢幕測試",
        "超级用户" to "超級使用者", "全部应用" to "全部應用程式", "已授权" to "已授權",
        "搜索应用名或包名" to "搜尋應用程式名稱或套件名", "系统" to "系統",
        "返回" to "返回", "重试" to "重試", "正在读取..." to "正在讀取…",
        "取消" to "取消", "确定" to "確定", "匹配" to "匹配", "说明" to "說明",
        "当前内核有匹配模块" to "目前核心有匹配模組", "当前内核没有匹配模块" to "目前核心沒有匹配模組",
        "判定分支" to "判定分支", "内核模块矩阵" to "核心模組矩陣",
        "动效实验室" to "動效實驗室", "作弊哨兵" to "作弊哨兵", "系统守护" to "系統守護",
        "电池健康" to "電池健康", "设计容量" to "設計容量", "实际容量" to "實際容量",
        "循环次数" to "循環次數", "温度" to "溫度", "电压" to "電壓", "当前电量" to "目前電量",
        "已锁定" to "已鎖定", "引擎未响应" to "引擎未回應", "兼容模式" to "相容模式",
        "效果" to "效果", "缓动" to "緩動", "时长" to "時長",
        "防开发者选项被重置" to "防開發者選項被重置", "Root 掉线守护" to "Root 斷線守護",
        "环境检查通过" to "環境檢查通過", "当前不可刷入" to "目前不可刷入",
        "正在读取..." to "正在讀取…"
    )

    private val en = mapOf(
        "主页" to "Home", "工具" to "Tools", "超管" to "Superuser", "模块" to "Modules", "设置" to "Settings",
        "语言设置" to "Language", "选择你的语言" to "Choose your language",
        "授权" to "Granted", "未工作" to "Not running", "检测中..." to "Detecting…",
        "刷入镜像" to "Flash image", "底层重载" to "Reload engine", "模块挂载" to "Mount modules",
        "内核接管" to "Kernel takeover", "接管底层" to "Take over core",
        "系统与设备信息" to "System & device", "设备型号" to "Device", "系统版本" to "Android",
        "内核版本" to "Kernel", "系统指纹" to "Fingerprint",
        "调优与工具" to "Tools", "日常管理" to "Everyday", "系统性能" to "Performance",
        "进阶玩法" to "Advanced", "设备与测试" to "Device & tests",
        "快捷指令" to "Quick actions", "App 管理" to "App manager", "Root 审计" to "Root audit",
        "权限审计" to "Permission audit", "CPU 调度" to "CPU governor", "系统魔改" to "System tweaks",
        "分区备份" to "Partition backup", "终端命令" to "Terminal", "内核模块" to "Kernel modules",
        "方舟快照" to "Ark snapshot", "隐藏与反检测" to "Hiding & anti-detect",
        "硬件信息" to "Hardware info", "传感器" to "Sensors", "屏幕测试" to "Screen test",
        "超级用户" to "Superuser", "全部应用" to "All apps", "已授权" to "Granted",
        "搜索应用名或包名" to "Search name or package", "系统" to "system",
        "返回" to "Back", "重试" to "Retry", "正在读取..." to "Loading…",
        "取消" to "Cancel", "确定" to "OK", "匹配" to "match", "说明" to "Notes",
        "当前内核有匹配模块" to "A matching module is bundled",
        "当前内核没有匹配模块" to "No module matches this kernel",
        "判定分支" to "Detected branch", "内核模块矩阵" to "Kernel module matrix",
        "动效实验室" to "Motion lab", "作弊哨兵" to "Cheat sentinel", "系统守护" to "System guardian",
        "电池健康" to "Battery health", "设计容量" to "Design capacity", "实际容量" to "Full capacity",
        "循环次数" to "Cycles", "温度" to "Temp", "电压" to "Voltage", "当前电量" to "Level",
        "已锁定" to "Locked", "引擎未响应" to "Engine unresponsive", "兼容模式" to "Compatibility mode",
        "效果" to "Effect", "缓动" to "Easing", "时长" to "Duration",
        "防开发者选项被重置" to "Keep developer options on", "Root 掉线守护" to "Root watchdog",
        "环境检查通过" to "Environment check passed", "当前不可刷入" to "Flashing not allowed",
        "正在读取..." to "Loading…"
    )

    private val ja = mapOf(
        "主页" to "ホーム", "工具" to "ツール", "超管" to "スーパーユーザー", "模块" to "モジュール", "设置" to "設定",
        "语言设置" to "言語", "选择你的语言" to "言語を選択",
        "授权" to "許可済み", "未工作" to "停止中", "检测中..." to "検出中…",
        "刷入镜像" to "イメージ書き込み", "底层重载" to "エンジン再起動",
        "模块挂载" to "モジュールマウント", "内核接管" to "カーネル接管",
        "系统与设备信息" to "システムとデバイス", "设备型号" to "機種", "系统版本" to "Android",
        "内核版本" to "カーネル", "系统指纹" to "指紋",
        "返回" to "戻る", "重试" to "再試行", "正在读取..." to "読み込み中…",
        "取消" to "キャンセル", "确定" to "OK", "超级用户" to "スーパーユーザー"
    )

    private val ko = mapOf(
        "主页" to "홈", "工具" to "도구", "超管" to "슈퍼유저", "模块" to "모듈", "设置" to "설정",
        "语言设置" to "언어", "选择你的语言" to "언어 선택",
        "授权" to "승인됨", "未工作" to "중지됨", "检测中..." to "감지 중…",
        "刷入镜像" to "이미지 플래시", "底层重载" to "엔진 재시작",
        "模块挂载" to "모듈 마운트", "内核接管" to "커널 인수",
        "系统与设备信息" to "시스템 및 기기", "设备型号" to "기기", "系统版本" to "Android",
        "内核版本" to "커널", "系统指纹" to "지문",
        "返回" to "뒤로", "重试" to "다시 시도", "正在读取..." to "불러오는 중…",
        "取消" to "취소", "确定" to "확인", "超级用户" to "슈퍼유저"
    )

    private val ru = mapOf(
        "主页" to "Главная", "工具" to "Инструменты", "超管" to "Суперпользователь", "模块" to "Модули", "设置" to "Настройки",
        "语言设置" to "Язык", "选择你的语言" to "Выберите язык",
        "授权" to "Разрешено", "未工作" to "Не работает", "检测中..." to "Проверка…",
        "刷入镜像" to "Записать образ", "底层重载" to "Перезапуск движка",
        "模块挂载" to "Монтировать модули", "内核接管" to "Захват ядра",
        "系统与设备信息" to "Система и устройство", "设备型号" to "Устройство", "系统版本" to "Android",
        "内核版本" to "Ядро", "系统指纹" to "Отпечаток",
        "返回" to "Назад", "重试" to "Повторить", "正在读取..." to "Загрузка…",
        "取消" to "Отмена", "确定" to "ОК", "超级用户" to "Суперпользователь"
    )

    private val zako = mapOf(
        // 底部导航
        "主页" to "本小姐的地盘♡", "工具" to "杂鱼♡的玩具箱", "超管" to "讨·好·本小姐",
        "模块" to "塞模块嘛～", "设置" to "随·便·调·教",

        // 语言
        "语言设置" to "杂鱼还想换口音？就这点本事？", "选择你的语言" to "挑一个呀～连这都做不到吗？",

        // 状态
        "授权" to "哼，本小姐准了♡", "未工作" to "本小姐在打盹，杂鱼别吵",
        "检测中..." to "让本小姐瞅瞅杂鱼几斤几两…", "已授权" to "早就准了，还不快谢♡",
        "已锁定" to "被本小姐关小黑屋了呢～", "引擎未响应" to "引擎装死中，真没用呢",
        "兼容模式" to "凑合借用别人的壳", "环境检查通过" to "检查过了，能上",
        "当前不可刷入" to "现在不行哦，杂鱼就是杂鱼",

        // 功能
        "刷入镜像" to "把镜像塞进去呀～", "底层重载" to "把底层掀了重来！",
        "模块挂载" to "挂几个小玩意儿", "内核接管" to "内核归本小姐了♡",
        "接管底层" to "底层现在归我了", "快捷指令" to "使唤它干活",
        "App 管理" to "收拾这些应用", "Root 审计" to "查查谁在偷权",
        "权限审计" to "权限大扫除", "CPU 调度" to "调教 CPU",
        "系统魔改" to "想改就改", "分区备份" to "先存个档嘛",
        "终端命令" to "命令行，杂鱼看得懂吗", "内核模块" to "内核小玩意儿",
        "方舟快照" to "方舟存档点", "隐藏与反检测" to "藏好，别被看见",
        "硬件信息" to "这破机器什么配置", "传感器" to "摸摸它的传感器",
        "屏幕测试" to "戳屏幕玩", "超级用户" to "超级用户大人",
        "全部应用" to "所有杂鱼应用", "搜索应用名或包名" to "哪个杂鱼？报上名来",
        "系统" to "系统自带", "返回" to "退下吧，杂鱼♡", "重试" to "再来一次吧，看你可怜",
        "正在读取..." to "本小姐正在翻…", "取消" to "算了不要了",
        "确定" to "就这么定了", "匹配" to "正好对上了", "说明" to "听好，只说一遍",

        // 设备
        "系统与设备信息" to "把这两机器底细全扒出来", "设备型号" to "什么机型",
        "系统版本" to "系统", "内核版本" to "内核", "系统指纹" to "指纹",
        "调优与工具" to "调教与玩具", "日常管理" to "日常杂活",
        "系统性能" to "让它跑快点", "进阶玩法" to "进阶玩法♡",
        "设备与测试" to "拆开看看",

        // 内核
        "当前内核有匹配模块" to "这台机器的内核，本小姐有对应的模块",
        "当前内核没有匹配模块" to "哎，这内核本小姐没有对应的模块",
        "判定分支" to "认出来的分支", "内核模块矩阵" to "内核小玩意儿一览",

        // 新增
        "动效实验室" to "本小姐的动效小把戏", "作弊哨兵" to "抓外挂的杂鱼探测器",
        "系统守护" to "看门狗在值班呢", "电池健康" to "电池还剩几口气",
        "设计容量" to "出厂时能装多少", "实际容量" to "现在还能装多少",
        "循环次数" to "被折腾了多少回", "温度" to "烫不烫呀～",
        "电压" to "电压啦", "当前电量" to "还剩多少电",
        "效果" to "花招", "缓动" to "节奏感", "时长" to "快慢",
        "防开发者选项被重置" to "谁敢关开发者选项", "Root 掉线守护" to "盯着 Root 别跑掉",
        "崩溃日志" to "它怎么死的？好弱哦", "导出诊断报告" to "把家底交出去",
        "忽略电池优化" to "别让系统偷偷把它掐死", "检查模块更新" to "看看有没有新玩意儿",
        "材质风格" to "卡片长啥样", "液态玻璃" to "会流动的玻璃",
        "柔光玻璃" to "软绵绵的玻璃", "毛玻璃" to "毛玻璃啦",
        "实心" to "实心的", "无材质" to "什么都不给",
        "主题色" to "换个颜色", "卡片圆角" to "圆一点还是方一点",
        "用户" to "用户们", "总计" to "一共", "已冻结" to "被冻住了",
        "安装" to "装上", "卸载" to "扔了", "冻结" to "冻起来",
        "打开" to "打开看看", "应用详情" to "查它底细",
        "成功" to "成了♡夸夸本小姐", "失败" to "翻了车呢，杂鱼", "警告" to "喂喂"
    )

    /** 供覆盖率统计用：这一档语言到底录了多少条。 */
    private fun tableOf(mode: LanguageMode): Map<String, String> = when (mode) {
        LanguageMode.ZH_CN -> emptyMap()
        LanguageMode.ZH_TW -> zhTw
        LanguageMode.EN -> en
        LanguageMode.JA -> ja
        LanguageMode.KO -> ko
        LanguageMode.RU -> ru
        LanguageMode.ZAKO -> zako
    }

    fun coverage(mode: LanguageMode): Int = when (mode) {
        LanguageMode.ZH_CN -> totalKeys()
        else -> tableOf(mode).size
    }

    fun totalKeys(): Int = en.size

    /** 兼容旧值：引导页曾把语言写成 "NORMAL"，它不是合法枚举。 */
    fun modeOf(raw: String?): LanguageMode = when (raw) {
        null, "", "NORMAL", "ZH_CN" -> LanguageMode.ZH_CN
        else -> runCatching { LanguageMode.valueOf(raw) }.getOrDefault(LanguageMode.ZH_CN)
    }

    fun currentMode(): LanguageMode = modeOf(AppSettings.currentLanguage)

    /**
     * 主入口。
     *
     * @param original 简体中文原文（同时作为词条 key）
     */
    fun tr(original: String, mode: LanguageMode): String {
        if (mode == LanguageMode.ZH_CN) return original

        tableOf(mode)[original]?.let { return it }

        return when (mode) {
            LanguageMode.ZAKO -> zakoize(original)
            // 非中文语言缺词时回落英文，比回落中文对用户更友好
            else -> en[original] ?: original
        }
    }

    /**
     * 杂鱼语气兜底。
     *
     * 词表只覆盖高频词条；这个函数保证**没收录的文案也带杂鱼腔**，
     * 而不是像以前那样一半杂鱼一半正经中文。规则刻意保守：
     *   - 纯 ASCII（路径、包名、版本号）一律不动
     *   - 只对 2~12 个汉字的短句做语气微调
     *   - 已经在词表里的不会被二次加工（[tr] 先查表）
     */
    fun zakoize(original: String): String {
        val text = original.trim()
        if (text.isEmpty()) return original
        // 纯 ASCII（数字、包名、路径、英文）不处理，加了语气反而怪
        if (text.all { it.code < 128 }) return original
        val hanCount = text.count { it.code in 0x4E00..0x9FFF }
        if (hanCount < 2) return original

        // 用字符串自身的哈希做**确定性**选择：
        // 随机数会导致每次重组都变一个说法，界面跟着闪；哈希保证同一条文案永远同一句。
        val h = text.hashCode() and 0x7fffffff
        val tails = listOf("哦～", "啦～", "呢♡", "的说", "嘛", "，懂？")
        val tail = tails[h % tails.size]

        // 之前这里有个 `if (text.length > 14) return original` —— 长句直接原样返回，
        // 于是描述、提示、说明这类长文案在杂鱼模式下完全没有变化，用户看到的
        // 就是"只覆盖了一部分"。现在长句也处理：把语气加在句尾，不动原文内容。
        val isLong = text.length > 14

        val core = when {
            text.endsWith("。") -> text.dropLast(1) + tail + "。"
            text.endsWith("！") -> text.dropLast(1) + tail + "！"
            text.endsWith("？") -> text.trimEnd('？') + tails[(h + 1) % tails.size] + "？"
            text.endsWith("成功") -> text + "，本小姐出手当然成"
            text.endsWith("失败") -> text + "，杂鱼真笨"
            text.endsWith("中") -> text + "呢…"
            text.endsWith("错误") -> text + "，自己看着办"
            else -> text + tail
        }

        // 长句额外补一句收尾，让整段读起来是"本小姐在说话"而不是原文照搬
        return if (isLong) "$core ${listOf("就这些，记好了。", "听懂了吗，杂鱼。", "别让本小姐重复第二遍。")[h % 3]}"
        else core
    }

    /** 语言选择页的实时预览行。 */
    fun preview(mode: LanguageMode): String =
        tr("主页", mode) + " · " + tr("内核模块", mode) + " · " + tr("返回", mode)
}
