import re
from pathlib import Path

print("🚀 正在执行终极扒衣：清理所有 ZakoZakoZakoText 套娃...")

base_dir = Path('app/src/main/java')
fixed_count = 0

for kt in base_dir.rglob('*.kt'):
    content = kt.read_text()
    orig = content

    # 正则匹配：Zako 后面跟任意数量的 Zako，再跟 Text
    # 比如：ZakoText, ZakoZakoText, ZakoZakoZakoText 等全部替换为 ZakoText
    content = re.sub(r'(?:Zako)+Text', 'ZakoText', content)
    
    # 顺便把 import 里的也修一下
    content = re.sub(r'(?:Zako)+Text', 'ZakoText', content)

    if content != orig:
        kt.write_text(content)
        fixed_count += 1

print(f"✅ 扒衣完成！共修复了 {fixed_count} 个文件里的套娃问题。")
print("🚀 直接执行 ./gradlew assembleDebug 起飞！")
