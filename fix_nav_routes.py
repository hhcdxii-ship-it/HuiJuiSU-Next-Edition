from pathlib import Path
import re

print("🚀 正在修复导航路由缺失问题...")

# 1. 修改 MainActivity.kt，补全 NavHost 里的路由
main_path = Path('app/src/main/java/com/huijui/sunext/edition/MainActivity.kt')
content = main_path.read_text()

# 确保引入了需要跳转的页面
imports = """
import com.huijui.sunext.edition.feature.settings.ThemeSettingsScreen
import com.huijui.sunext.edition.feature.settings.LanguageSettingsScreen
"""
if 'import com.huijui.sunext.edition.feature.settings.ThemeSettingsScreen' not in content:
    content = content.replace('package com.huijui.sunext.edition', 'package com.huijui.sunext.edition' + imports)

# 找到 NavHost 里注册 composable 的地方
# 假设原代码里有 composable("settings") { ... }
# 我们直接在它后面追加两个路由

old_nav = 'composable("settings") {'
new_nav = '''composable("themesettings") {
                ThemeSettingsScreen()
            }
            composable("language_settings") {
                LanguageSettingsScreen()
            }
            composable("settings") {'''

if 'composable("themesettings")' not in content:
    # 替换原来的 settings 路由，把新的加在前面
    content = content.replace(old_nav, new_nav)
    main_path.write_text(content)
    print("✅ MainActivity.kt 已补全 themesettings 和 language_settings 路由！")
else:
    print("⚠️ 路由似乎已经存在。")

