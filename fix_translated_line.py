import re
from pathlib import Path

print("🚀 正在修复 TranslatedLine 的语法断裂...")

files = [
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiFlashManager.kt',
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiModuleInstaller.kt'
]

for path in files:
    p = Path(path)
    if p.exists():
        content = p.read_text()
        
        # 核心修复：把 TranslatedLine."xxx" 变成 TranslatedLine.Text("xxx")
        # 匹配 TranslatedLine. 后面直接跟着引号的情况
        new_content = re.sub(r'TranslatedLine\.\s*(".*?")', r'TranslatedLine.Text(\1)', content)
        
        # 如果存在 TranslatedLine.zakoString("...") 这种奇葩变异，也一并干掉
        new_content = re.sub(r'TranslatedLine\.zakoString\("(.*?)"\)', r'TranslatedLine.Text("\1")', new_content)
        
        if new_content != content:
            p.write_text(new_content)
            print(f"✅ 成功修复 {p.name} 的语法错误！")
        else:
            print(f"⚠️ {p.name} 似乎不需要修复，或者格式变化了。")

print("🎉 修复完毕！马上执行 ./gradlew assembleDebug 即可！")
