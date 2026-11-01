import os
import re
from pathlib import Path

print("🚀 开始执行高精度 Zako 安全替换（跳过底层，只动 UI 和提示）...")

# 1. 扩充字典 (加入刚才扫描出的高频 UI 词汇)
zako_path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/language/ZakoText.kt')
content = zako_path.read_text()
new_dict = '''
    // 🚀 高频 UI 词汇
    "设备型号" to "杂鱼的小破机",
    "系统版本" to "烂橘子系统",
    "内核版本" to "底层杂鱼",
    "系统指纹" to "没用的指纹",
    "未工作" to "连工作都不会的废柴~",
    "工作中 (HUI)" to "本小姐在干活啦",
    "工作中 (HUI-Ultra)" to "本小姐在疯狂干活啦",
    "未获取权限" to "没权限的废物",
    "日常管理" to "杂鱼的日常垃圾",
    "系统性能" to "累死你CPU",
    "设备与测试" to "你的破铜烂铁",
    "快捷指令" to "快速调教",
    "App 管理" to "杂鱼破App",
    "Root 审计" to "查你底裤",
    "权限审计" to "看多贪婪",
    "分区备份" to "留个种",
    "终端命令" to "敲键盘",
    "硬件信息" to "看破铜烂铁",
    "传感器" to "别乱摸！",
    "屏幕测试" to "看瞎你",
    "配置与个性化" to "调教与个性化",
    "HUI 独有特性" to "本小姐的独有魅力",
    "重置与危险操作" to "毁灭与作死",
    "恢复出厂设置" to "想逃避现实？",
    "关于" to "关于本小姐",
    "每日管理" to "每日挨骂",
    "内存" to "破内存",
    "存储" to "破存储",
    "网络" to "破网络",
    "屏幕" to "破屏幕",
    "处理器" to "破处理器",
    "刷新" to "再戳一下",
    "操作成功" to "居然搞定了",
    "操作失败" to "果然失败了，杂鱼就是杂鱼",
    "已删除" to "清干净了",
    "删除失败" to "删不掉吧笨蛋",
    "备份超时（120s）" to "太慢了，本小姐不等了",
    "恢复成功，建议重启设备" to "搞定了，自己重启去",
    "恢复失败或超时" to "又失败了，没救了",
    "请到系统设置中授予通知权限" to "没权限还想让本小姐通知你？",
    "监控通知已开启" to "本小姐开始盯着你了",
    "监控通知已关闭" to "本小姐懒得看你",
    "已切换到性能模式" to "榨干你的CPU",
    "已切换到均衡模式" to "中庸的杂鱼",
    "已切换到省电模式" to "省点电吧废物",
    "连 Root 都没有，无法执行指令" to "连 Root 都没，还想使唤本小姐？",
    "执行超时" to "太慢了，本小姐不等了",
    "该分类下没有应用" to "连个破 App 都没有",
    "解冻" to "解冻杂鱼",
    "冻结" to "冻住杂鱼",
    "卸载" to "滚粗",
    "恢复" to "复活吧",
    "系统" to "系统大爷",
    "用户" to "用户杂鱼",
    "已冻结" to "冻死了",
    "已卸载" to "滚远了",
    "全部" to "所有的垃圾",
    "未授予" to "别想了杂鱼",
    "已授予" to "算你识相",
    "低风险" to "没啥大不了",
    "中风险" to "有点危险哦",
    "高风险" to "碰了会死哦",
    "点击应用可跳转到系统设置中管理权限。此页面只展示，不修改任何权限。" to "点进去自己看，本小姐懒得帮你改。",
    "没有应用" to "啥都没装，杂鱼~",
    "没有模块" to "没插模块哦~",
    "安装成功" to "塞进去了",
    "安装失败" to "塞不进去，杂鱼",
    "准备安装…" to "让本小姐准备一下…",
    "安装完成" to "搞定了",
'''
if '// 🚀 高频 UI 词汇' not in content:
    content = content.replace('    // 🚀 V6 引擎专属词条', new_dict + '\n    // 🚀 V6 引擎专属词条')
zako_path.write_text(content)
print("✅ ZakoText.kt 字典已扩充！")

# 2. 白名单安全替换逻辑
base_dir = Path('app/src/main/java')
modified_files = 0

# 绝对不许动的底层文件/目录白名单
exclude_dirs = ['core/root/HuijuiScriptRunner.kt', 'core/root/PartitionBackupManager.kt', 'core/root/FlashLogTranslator.kt', 'core/root/FlashGuard.kt', 'core/root/GkiChecker.kt', 'core/root/ExploitManager.kt']

for kt_file in base_dir.rglob('*.kt'):
    rel_path = str(kt_file.relative_to(base_dir))
    if kt_file.name in ['ZakoText.kt'] or any(ex in rel_path for ex in exclude_dirs):
        continue

    content = kt_file.read_text()
    original = content

    # 安全替换 Toast: Toast.makeText(context, "中文", ...) -> Toast.makeText(context, zakoString("中文"), ...)
    def replace_toast(match):
        prefix = match.group(1)
        text = match.group(2)
        suffix = match.group(3)
        if re.search(r'[\u4e00-\u9fa5]', text) and 'zakoString' not in prefix:
            return f'Toast.makeText({prefix}, zakoString("{text}"){suffix}'
        return match.group(0)
    content = re.sub(r'Toast\.makeText\(([^,]+),\s*"([^"]+)"(\s*,)', replace_toast, content)

    # 安全替换 Compose 的 Text: Text("中文", ...) -> ZakoText("中文", ...)
    def replace_text(match):
        text = match.group(1)
        if re.search(r'[\u4e00-\u9fa5]', text) and 'zakoString' not in text:
            return f'ZakoText("{text}"'
        return match.group(0)
    content = re.sub(r'Text\("([^"]+)"', replace_text, content)
    
    # 安全替换 text = "中文"
    def replace_text_attr(match):
        attr = match.group(1)
        text = match.group(2)
        if re.search(r'[\u4e00-\u9fa5]', text) and 'zakoString' not in text:
            return f'{attr} = zakoString("{text}")'
        return match.group(0)
    content = re.sub(r'(text)\s*=\s*"([^"]+)"', replace_text_attr, content)

    # 安全替换一些 data class 的字符串 (如果是纯 UI 界面的话)
    # 比如 NavItem("home", "主页") -> NavItem("home", zakoString("主页"))
    def replace_nav(match):
        return f'NavItem({match.group(1)}, zakoString("{match.group(2)}"){match.group(3)}'
    content = re.sub(r'NavItem\(([^,]+),\s*"([^"]+)"(,?)', replace_nav, content)

    if content != original:
        # 自动补充 import
        if 'zakoString' in content and 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in content:
            content = content.replace('import androidx.compose', 'import com.huijui.sunext.edition.core.ui.language.zakoString\nimport androidx.compose', 1)
        if 'ZakoText(' in content and 'import com.huijui.sunext.edition.core.ui.language.ZakoText' not in content:
            content = content.replace('import androidx.compose', 'import com.huijui.sunext.edition.core.ui.language.ZakoText\nimport androidx.compose', 1)
            
        kt_file.write_text(content)
        modified_files += 1

print(f"🎉 安全替换完成！精准修复了 {modified_files} 个 UI 界面文件！")
print("🚀 直接执行 ./gradlew assembleDebug 即可。")
