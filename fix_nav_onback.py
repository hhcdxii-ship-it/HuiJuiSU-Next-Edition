from pathlib import Path

print("🚀 正在修复主题与语言页面的 onBack 参数...")

main_path = Path('app/src/main/java/com/huijui/sunext/edition/MainActivity.kt')
content = main_path.read_text()

# 修复 ThemeSettingsScreen
content = content.replace(
    'ThemeSettingsScreen()',
    'ThemeSettingsScreen(onBack = { navController.popBackStack() })'
)

# 修复 LanguageSettingsScreen
content = content.replace(
    'LanguageSettingsScreen()',
    'LanguageSettingsScreen(onBack = { navController.popBackStack() })'
)

main_path.write_text(content)
print("✅ 已为两个页面补全 onBack 回调！")
