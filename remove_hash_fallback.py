import re
from pathlib import Path

print("🚀 正在切除 ZakoText 的自动翻译兜底逻辑...")

path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/language/ZakoText.kt')
content = path.read_text()

# 匹配那个长得像大括号嵌套的 if (text.matches(Regex("""..."""))) 块
pattern = r'    if \(text\.matches\(Regex\(""".\*\[\\u4e00-\\u9fa5\]\.\*"""\)\)\)\s*\{[\s\S]*?return "\$text" \+ suffixes\[hash % suffixes\.size\]\s*\}\n'

# 把这段自动翻译逻辑删掉，换成什么都不做，直接返回原文本
replacement = "    // 🛑 智能兜底已废除：没在字典里的字，保持原样，不打乱 UI 语境\n"

new_content = re.sub(pattern, replacement, content)

if new_content != content:
    path.write_text(new_content)
    print("✅ 成功切除！现在 zakoString 只认精准匹配和正则拦截。")
else:
    print("⚠️ 没找到匹配的 Hash 兜底代码，请手动检查 ZakoText.kt。")
