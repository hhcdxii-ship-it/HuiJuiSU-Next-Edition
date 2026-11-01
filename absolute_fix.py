import re
from pathlib import Path

print("🚀 正在执行终极外科手术：拆分 zakoString 并强杀 onBack...")

# 1. 提取 zakoString 和字典到一个新文件 (去除 Compose 依赖)
old_zako_path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/language/ZakoText.kt')
new_zako_path = Path('app/src/main/java/com/huijui/sunext/edition/core/utils/ZakoString.kt')
new_zako_path.parent.mkdir(parents=True, exist_ok=True)

content = old_zako_path.read_text()

# 找到 private val zakoDict 到 fun zakoString 结束的地方
match = re.search(r'(private val zakoDict = mapOf\([\s\S]*?\)\n\nfun zakoString[\s\S]*?\n\})', content)

if match:
    dict_and_func = match.group(1)
    # 写入新文件
    new_file_content = f'''package com.huijui.sunext.edition.core.utils

import com.huijui.sunext.edition.core.utils.AppSettings
import kotlin.math.abs

{dict_and_func}
'''
    new_zako_path.write_text(new_file_content)
    print("✅ 成功将 zakoString 抽离到 core/utils/ZakoString.kt！")

# 2. 从老文件里删掉这块，只保留 Composable
if match:
    content = content.replace(match.group(1), '')
    content = content.replace('import com.huijui.sunext.edition.core.utils.AppSettings\n', '')
    content = content.replace('import kotlin.math.abs\n', '')
    # 重新引入新文件的 zakoString
    content = 'import com.huijui.sunext.edition.core.utils.zakoString\n' + content
    old_zako_path.write_text(content)
    print("✅ ZakoText.kt 已清理并重新引入！")

# 3. 全局替换所有的 zakoString import
base_dir = Path('app/src/main/java')
for kt in base_dir.rglob('*.kt'):
    c = kt.read_text()
    orig = c
    c = c.replace('import com.huijui.sunext.edition.core.ui.language.zakoString', 'import com.huijui.sunext.edition.core.utils.zakoString')
    if c != orig:
        kt.write_text(c)

# 4. 彻底干掉 MainActivity 的 onBack
main_kt = Path('app/src/main/java/com/huijui/sunext/edition/MainActivity.kt')
if main_kt.exists():
    c = main_kt.read_text()
    # 暴力删掉所有包含 onBack 的行
    lines = c.split('\n')
    new_lines = [l for l in lines if 'onBack' not in l]
    main_kt.write_text('\n'.join(new_lines))
    print("✅ MainActivity.kt 中的 onBack 相关代码已物理超度！")

print("🎉 终极拆分完成！直接执行 ./gradlew assembleDebug 起飞！")
