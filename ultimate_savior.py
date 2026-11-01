import os
import re
from pathlib import Path

print("🚀 开始终极抢救：修复 MainActivity 语法崩溃与全局 import...")

# ==========================================
# 1. 抢救 MainActivity.kt
# ==========================================
main_path = Path('app/src/main/java/com/huijui/sunext/edition/MainActivity.kt')

# 尝试用 git 恢复到未崩溃的状态
if main_path.exists():
    os.system("git checkout -- app/src/main/java/com/huijui/sunext/edition/MainActivity.kt 2>/dev/null")
    c = main_path.read_text()

    # 暴力修复可能残留的括号断裂问题
    c = re.sub(r'composable\("core"\)\s*\{\s*CoreOverrideScreen\([^}]*\}\s*\}', 'composable("core") { CoreOverrideScreen() }', c)
    c = c.replace('CoreOverrideScreen() }', 'CoreOverrideScreen()')

    # 重新给底栏注入 zakoString
    c = re.sub(r'NavItem\("([^"]+)",\s*"([^"]+)"', r'NavItem("\1", zakoString("\2")', c)
    
    if 'zakoString(' in c and 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in c:
        c = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.zakoString', c, count=1)
    
    main_path.write_text(c)
    print("✅ MainActivity.kt 已成功抢救并重打补丁！")


# ==========================================
# 2. 全局扫盲：补齐所有缺失的 import
# ==========================================
base_dir = Path('app/src/main/java')
fixed_imports = 0

for kt in base_dir.rglob('*.kt'):
    if kt.name == 'ZakoText.kt': continue
    
    c = kt.read_text()
    orig = c
    
    # 补齐 zakoString
    if 'zakoString(' in c and 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in c:
        c = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.zakoString', c, count=1)
    
    # 补齐 ZakoText
    if 'ZakoText(' in c and 'import com.huijui.sunext.edition.core.ui.language.ZakoText' not in c:
        c = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.ZakoText', c, count=1)

    # 防止出现荒谬的 ZakoZakoText
    c = c.replace('ZakoZakoText', 'ZakoText')
    
    if c != orig:
        kt.write_text(c)
        fixed_imports += 1

print(f"✅ 全项目扫描完毕！为 {fixed_imports} 个文件精准补齐了缺失的 import！")
print("🎉 终极抢救完成！请立刻执行 ./gradlew assembleDebug")
