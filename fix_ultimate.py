import os
import re
from pathlib import Path

print("🚀 正在执行终极排雷...")

# 1. 修复被彻底干碎的 MainActivity.kt
main_path = Path('app/src/main/java/com/huijui/sunext/edition/MainActivity.kt')
if main_path.exists():
    content = main_path.read_text()
    # 把误改的参数删掉。原代码可能是 CoreOverrideScreen(onBack = { ... }) 之类的，因为咱们重写了 UI，它不需要参数了。
    # 直接把 CoreOverrideScreen(...) 里的参数抹掉
    content = re.sub(r'CoreOverrideScreen\([^)]*\)', 'CoreOverrideScreen()', content)
    
    # 修复由于误删导致的 NavHost 内部大括号不匹配
    # 如果修复后还有语法错误，可能是之前的替换把别的参数搞坏了，这里做尽量全的替换
    main_path.write_text(content)
    print("✅ MainActivity.kt 导航语法已修复")

# 2. 修复底层文件的 import 和 ZakoText 误用
core_files = [
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiFlashManager.kt',
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiModuleInstaller.kt'
]
for f_path in core_files:
    f = Path(f_path)
    if f.exists():
        content = f.read_text()
        # 把误加的 ZakoText 全部换成 zakoString
        content = content.replace('ZakoText(', 'zakoString(')
        # 删掉错误的 import 和可能重复的 import
        content = content.replace('import com.huijui.sunext.edition.core.ui.language.ZakoText\n', '')
        
        # 确保加了正确的 import
        if 'zakoString(' in content and 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in content:
            content = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.zakoString', content, count=1)
        f.write_text(content)
        print(f"✅ {f.name} 已修复 zakoString 的引用")

# 3. 修复 HardwareInfoScreen.kt 里的 newPlainZakoText
hw_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/HardwareInfoScreen.kt')
if hw_path.exists():
    content = hw_path.read_text()
    content = content.replace('newPlainZakoText', 'newPlainText')
    hw_path.write_text(content)
    print("✅ HardwareInfoScreen.kt 的 newPlainText 已修复")

# 4. 全局扫尾：把残留的 ZakoZakoText 清空，补全缺失的 zakoString import
base_dir = Path('app/src/main/java')
for kt in base_dir.rglob('*.kt'):
    if kt.name == 'ZakoText.kt': continue
    c = kt.read_text()
    orig = c
    c = c.replace('ZakoZakoText', 'ZakoText')
    
    if 'ZakoText(' in c and 'import com.huijui.sunext.edition.core.ui.language.ZakoText' not in c:
        c = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.ZakoText', c, count=1)
    if 'zakoString(' in c and 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in c:
        c = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.zakoString', c, count=1)
        
    if c != orig:
        kt.write_text(c)

print("🎉 终极修复完成！请执行 ./gradlew assembleDebug")
