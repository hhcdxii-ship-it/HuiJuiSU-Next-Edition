import re
from pathlib import Path

print("🚀 正在注入毛玻璃控制台与浅色导航栏...")

# ==========================================
# 1. 修改 CoreOverrideScreen.kt 控制台为毛玻璃
# ==========================================
ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = ui_path.read_text()

# 替换控制台 Box 的背景和边框，改为半透明毛玻璃质感
old_console = r'\.border\(1\.dp, Color\(0xFF2C2C35\), RoundedCornerShape\(12\.dp\)\)\s*\.background\(Color\(0xFF050505\), RoundedCornerShape\(12\.dp\)\)'
new_console = r'.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))\n                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))'
content = re.sub(old_console, new_console, content)

# 替换 ActionCard 的背景，也改成毛玻璃
old_card = r'\.background\(Color\(0xFF1E1E24\)\)\s*// 毛玻璃感的深灰\s*\.border\(1\.dp, Color\(0xFF333333\), RoundedCornerShape\(16\.dp\)\)'
new_card = r'.background(Color.White.copy(alpha = 0.08f))\n            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))'
content = re.sub(old_card, new_card, content)

# 顺便把整页的纯黑背景改成深色半透明，让底部的壁纸透出来
content = content.replace('.background(Color(0xFF0F0F11)) // 深邃背景', '.background(Color.Black.copy(alpha = 0.6f)) // 透出底层的深色半透明')

ui_path.write_text(content)
print("✅ CoreOverrideScreen.kt 控制台与卡片已升级为毛玻璃质感！")

# ==========================================
# 2. 寻找并修改 LiquidNavigationBar.kt 为浅色
# ==========================================
nav_path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/component/LiquidNavigationBar.kt')
if nav_path.exists():
    nav_content = nav_path.read_text()
    
    # 把深色的背景色替换成浅色/半透明白色
    nav_content = re.sub(r'Color\(0xFF[0-9A-Fa-f]{6}\)', 'Color.White.copy(alpha = 0.85f)', nav_content)
    
    # 为了防止文字/图标看不清，把图标的默认颜色强制改为黑色系
    # 如果你代码里用了 MaterialTheme.colorScheme.onSurface 这种，可以不用改，但我们暴力一点
    nav_content = nav_content.replace('tint = Color.White', 'tint = Color.Black')
    nav_content = nav_content.replace('color = Color.White', 'color = Color.Black')
    
    nav_path.write_text(nav_content)
    print("✅ LiquidNavigationBar.kt 已调整为浅色/半透明白色背景，图标黑色！")
else:
    # 如果 LiquidNavigationBar 不在这个路径，可能在其他地方，直接全局扫
    print("⚠️ 找不到 LiquidNavigationBar.kt，尝试全局替换导航栏背景色...")
    for f in Path('app/src/main/java').rglob('*.kt'):
        c = f.read_text()
        if 'NavigationBar(' in c or 'BottomNavigation(' in c:
            c = c.replace('containerColor = Color(0xFF1E1E24)', 'containerColor = Color.White.copy(alpha = 0.85f)')
            c = c.replace('containerColor = Color.Black', 'containerColor = Color.White.copy(alpha = 0.85f)')
            f.write_text(c)

print("🎉 双修完成！直接执行 ./gradlew assembleDebug 即可！")
