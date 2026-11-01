import re
from pathlib import Path

print("🚀 正在重写 onboardingPages 列表，修复语法撕裂...")

path = Path('app/src/main/java/com/huijui/sunext/edition/feature/onboarding/OnboardingScreen.kt')
content = path.read_text()

correct_pages = """private val onboardingPages = listOf(
    OnboardingPageData(PageType.CONFIG_LANGUAGE, Icons.Default.Translate, Color(0xFFA855F7), "STEP 1 / 3",
        "对话风格", "正常模式 还是 杂鱼模式？", "随时可以在设置里改"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Security, Color(0xFFF59E0B), "WELCOME",
        "欢迎回家，机友", "从今天起，你的设备真正属于你",
        "HuiJuiSU Next 不只是一个 Root 管理器，它是一个有温度、有生命的控制台。接下来 13 步，带你认识它、配置它、拥有它。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Memory, Color(0xFFEA8A00), "PURE KERNEL",
        "纯血内核，自己写的那种", "脱离 KSU / Magisk，一行行堆出来的 HUI.ko",
        "自研 C 内核模块 + Hsud 守护进程 + 一键热接管。没有套皮，没有借用，每一行都是自己干的。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Folder, Color(0xFF3B82F6), "HOT MOUNT",
        "模块热挂载，零重启", "刷完就能用，不用再等那一分钟",
        "本地 ZIP 直接热挂载，配合 HUI 内核接管，全程零重启体验。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.AdminPanelSettings, Color(0xFF6366F1), "SUPERUSER",
        "每个应用，你说得算", "精细到单个应用的 Root 权限",
        "读取真实应用图标，开关一拨，授权即刻生效。谁该有、谁不该有，你心里有数。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Build, Color(0xFFA855F7), "PATCHER",
        "镜像修补，一键生成", "init_boot / boot 重打包，电脑刷入",
        "A13+ 强制走 init_boot，解包、替换、注入 rc、重打包全自动。防呆拦截已在底层生效。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Fingerprint, Color(0xFFEC4899), "DETECTOR",
        "环境检测，硬核到底", "KSU / Magisk / APatch / 越狱，一个都跑不掉",
        "RootEnvironmentDetector + RootEngineVerifier 双保险，检查专属进程、属性、/dev/huijui 设备节点。"),
    OnboardingPageData(PageType.CONFIG_ACCENT, Icons.Default.Palette, Color(0xFFEF4444), "STEP 2 / 3",
        "挑一个主题色", "四种性格，你的偏好", "可在设置里随时更换"),
    OnboardingPageData(PageType.CONFIG_MATERIAL, Icons.Default.Layers, Color(0xFF3B82F6), "STEP 3 / 3",
        "材质风格", "卡片长什么样，你说了算", "毛玻璃 / 实心 / 无材质三档"),
    OnboardingPageData(PageType.INTRO, Icons.Default.AdminPanelSettings, Color(0xFFF59E0B), "FIRST RUN",
        "首次进入，请放行 Root", "只有授权后才能解锁全部功能",
        "如果没授权，底部「超管」入口会变灰。点击能量球刷新状态时，系统弹窗直接允许即可。"),
    OnboardingPageData(PageType.TIPS, Icons.Default.TipsAndUpdates, Color(0xFF0EA5B7), "PRO TIPS",
        "几个小技巧", "老司机都这么用"),
    OnboardingPageData(PageType.FINAL, Icons.Default.Celebration, Color(0xFF16A34A), "READY",
        "准备好了吗？", "接下来，交给 HUI", "所有配置已保存，点击右下角进入你的纯血控制台。")
)"""

# 匹配从 private val onboardingPages = listOf( 到其闭合的括号
pattern = r'private val onboardingPages = listOf\([\s\S]*?\n\)'
new_content = re.sub(pattern, correct_pages, content)

path.write_text(new_content)
print("✅ onboardingPages 列表结构已全量重写并修复！")
print("🚀 直接执行 ./gradlew assembleDebug 即可！")
