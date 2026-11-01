import os
import re
from pathlib import Path

print("🚀 正在执行绝对最终修复：彻底解决 core/root 的 zakoString 引用问题...")

# 1. 创建一个纯工具类 ZakoStringUtils.kt (绝对不依赖 Compose)
utils_path = Path('app/src/main/java/com/huijui/sunext/edition/core/utils/ZakoStringUtils.kt')
utils_path.parent.mkdir(parents=True, exist_ok=True)

utils_content = '''package com.huijui.sunext.edition.core.utils

import kotlin.math.abs

object ZakoStringUtils {
    private val zakoDict = mapOf(
        "主页" to "杂鱼的狗窝", "工具" to "玩具箱", "超管" to "大小姐", "模块" to "插模块", "设置" to "调教",
        "🚀 一键智能提取并刷入" to "把破镜像塞进去！", "工作中 (HUI)..." to "本小姐在干活啦...",
        "HuiJuiSU Next - 引擎重载" to "本小姐的底层重载", "纯血独立 / 全自动 LKM 注入" to "纯血杂鱼，自动塞入内核"
    )

    fun getZakoString(text: String): String {
        if (com.huijui.sunext.edition.core.utils.AppSettings.currentLanguage != "ZAKO") return text
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

        if (text.matches(Regex(""".*[\\u4e00-\\u9fa5].*"""))) {
            val hash = abs(text.hashCode())
            val suffixes = listOf(" 懂不懂啊？", " 真是拿你没办法~", " 杂鱼就是杂鱼。", " 哼，本小姐才不想管你呢！")
            return "$text" + suffixes[hash % suffixes.size]
        }
        return text
    }
}

// 暴露一个全局顶层函数，方便直接调用
fun zakoString(text: String): String = ZakoStringUtils.getZakoString(text)
'''
utils_path.write_text(utils_content)
print("✅ 纯血无依赖的 ZakoStringUtils.kt 已创建！")

# 2. 让 ZakoText.kt 调用这个纯工具类
zako_ui_path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/language/ZakoText.kt')
ui_content = zako_ui_path.read_text()
ui_content = ui_content.replace('fun zakoString(text: String): String {', 'import com.huijui.sunext.edition.core.utils.zakoString\n\nfun zakoString_legacy(text: String): String {')
ui_content = ui_content.replace('text = zakoString(text)', 'text = com.huijui.sunext.edition.core.utils.zakoString(text)')
zako_ui_path.write_text(ui_content)
print("✅ ZakoText.kt 已清理内部实现，改为调用纯工具类！")

# 3. 强制修复核心文件 HuijuiFlashManager.kt 和 HuijuiModuleInstaller.kt
target_files = [
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiFlashManager.kt',
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiModuleInstaller.kt'
]

for f_path in target_files:
    f = Path(f_path)
    if f.exists():
        c = f.read_text()
        lines = c.split('\n')
        # 找到 package 的位置
        pkg_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('package '):
                pkg_idx = i
                break
                
        if pkg_idx != -1:
            # 删掉所有错误的旧 import
            lines = [l for l in lines if 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in l]
            # 在 package 后面强行插入正确的 import
            lines.insert(pkg_idx + 1, '')
            lines.insert(pkg_idx + 2, 'import com.huijui.sunext.edition.core.utils.zakoString')
            f.write_text('\n'.join(lines))
            print(f"✅ {f.name} 已强制指向纯工具类！")

# 4. 全局扫尾，把任何引用旧包的地方全改掉
base_dir = Path('app/src/main/java')
for kt in base_dir.rglob('*.kt'):
    if kt.name in ['ZakoStringUtils.kt', 'ZakoText.kt']: continue
    c = kt.read_text()
    orig = c
    c = c.replace('import com.huijui.sunext.edition.core.ui.language.zakoString', 'import com.huijui.sunext.edition.core.utils.zakoString')
    if c != orig:
        kt.write_text(c)

print("🎉 终极修复彻底完成！直接执行 ./gradlew assembleDebug！")
