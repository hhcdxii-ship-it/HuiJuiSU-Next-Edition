import os
import re
from pathlib import Path

print("🚀 正在将底栏图标与文字强制重置为纯白...")

# 寻找 LiquidNavigationBar 或其他底栏组件
files = [
    'app/src/main/java/com/huijui/sunext/edition/core/ui/component/LiquidNavigationBar.kt',
    'app/src/main/java/com/huijui/sunext/edition/MainActivity.kt'
]

for path in files:
    p = Path(path)
    if not p.exists(): continue
    content = p.read_text()
    orig = content
    
    # 匹配 Icon 的 tint 参数，强制改为 Color.White
    content = re.sub(r'(tint\s*=\s*)([^,)]+)', r'\1Color.White', content)
    
    # 匹配 Text 的 color 参数，强制改为 Color.White
    content = re.sub(r'(color\s*=\s*)([^,)]+)', r'\1Color.White', content)

    if content != orig:
        p.write_text(content)
        print(f"✅ {p.name} 的图标与文字颜色已强制修正为纯白色！")

print("🎉 修改完成！直接执行 ./gradlew assembleDebug 重新安装体验！")
