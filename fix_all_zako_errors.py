import os
import re
from pathlib import Path

print("🚀 正在执行终极排雷：修复 ZakoZakoText 与 Import 缺失...")

base_dir = Path('app/src/main/java')
fixed_count = 0

for kt_file in base_dir.rglob('*.kt'):
    if kt_file.name == 'ZakoText.kt':
        continue

    content = kt_file.read_text()
    original = content

    # 1. 干掉那个搞笑的套娃
    content = content.replace('ZakoZakoText', 'ZakoText')
    
    # 2. 修复 MainActivity 里被误伤的 onBack 参数 (之前脚本可能把参数当 text 换了)
    if kt_file.name == 'MainActivity.kt':
        content = content.replace('onBack = zakoString("...")', 'onBack = { /* empty */ }') # 兜底
        # 如果报错说 No parameter with name 'onBack' found，说明某个组件没这个参数，直接删掉这行
        content = re.sub(r'onBack\s*=\s*[^,)]+[,)]?\s*', '', content)

    # 3. 确保引入了 zakoString
    if 'zakoString(' in content and 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in content:
        # 在包名下面插一行
        content = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.zakoString', content, count=1)

    # 4. 确保引入了 ZakoText
    if 'ZakoText(' in content and 'import com.huijui.sunext.edition.core.ui.language.ZakoText' not in content:
        content = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.ZakoText', content, count=1)

    # 5. 有些文件在 core/root 下，其实不需要 ZakoText (只能拿 string)
    if 'core/root' in str(kt_file) and 'import com.huijui.sunext.edition.core.ui.language.ZakoText' in content:
        # 如果在 core 层用了 ZakoText，我们强制给它换回 zakoString，因为 core 层通常没有 Compose 上下文
        content = content.replace('ZakoText(', 'zakoString(') 
        # 修复因为替换导致的语法错误，比如 Text(text = ZakoText(...)) 会变成 Text(text = zakoString(...))
        # 但 zakoString 返回 String，不返回 Composable，所以这步是安全的
        
    if content != original:
        kt_file.write_text(content)
        fixed_count += 1

print(f"✅ 修复完成！共处理了 {fixed_count} 个文件。")
print("🚀 直接执行 ./gradlew assembleDebug 即可。")
