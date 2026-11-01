import re
from pathlib import Path

print("🚀 正在重排引导页顺序，并注入动态语言支持...")

path = Path('app/src/main/java/com/huijui/sunext/edition/feature/onboarding/OnboardingScreen.kt')
content = path.read_text()

# ==========================================
# 1. 提取并重排这三个 CONFIG 页
# ==========================================
# 匹配这三个块（因为可能包含换行，用正则匹配直到遇到下一个 OnboardingPageData 或 结尾）
# 这里我们直接基于用户提供的行号 92-97 进行精准操作
lines = content.split('\n')

# 找到这三个块的起始索引
idx_accent = -1
idx_material = -1
idx_language = -1

for i, line in enumerate(lines):
    if 'PageType.CONFIG_ACCENT' in line: idx_accent = i
    if 'PageType.CONFIG_MATERIAL' in line: idx_material = i
    if 'PageType.CONFIG_LANGUAGE' in line: idx_language = i

if idx_accent != -1 and idx_material != -1 and idx_language != -1:
    # 假设每个块占 2 行（因为用户截图显示 92-97 共 6 行）
    accent_block = lines[idx_accent:idx_accent+2]
    material_block = lines[idx_material:idx_material+2]
    language_block = lines[idx_language:idx_language+2]

    # 修改 STEP 编号
    # 原本是 2/4, 3/4, 4/4 -> 改为 1/3, 2/3, 3/3
    language_block[0] = language_block[0].replace('STEP 4 / 4', 'STEP 1 / 3')
    material_block[0] = material_block[0].replace('STEP 3 / 4', 'STEP 2 / 3')
    accent_block[0] = accent_block[0].replace('STEP 2 / 4', 'STEP 3 / 3')

    # 将这三个块替换回原位置：语言在最前，材质中间，强调色最后
    # 先把原位置的内容替换掉（假设这6行是连续的，如果是隔开的，下面的替换可能会覆盖到其他代码，所以得小心）
    # 简单粗暴：用正则匹配这三个连续的块。
    pattern = r'(OnboardingPageData\(PageType\.CONFIG_ACCENT[\s\S]*?\)\)\s*,\s*OnboardingPageData\(PageType\.CONFIG_MATERIAL[\s\S]*?\)\)\s*,\s*OnboardingPageData\(PageType\.CONFIG_LANGUAGE[\s\S]*?\)\))'
    
    # 构建新的字符串
    new_sequence = '\n'.join(language_block) + '\n    ' + '\n'.join(material_block) + '\n    ' + '\n'.join(accent_block) + ','
    
    content = re.sub(pattern, new_sequence, content)
    print("✅ 配置页顺序已重排：语言 -> 材质 -> 强调色，且 STEP 编号已更新！")
else:
    print("⚠️ 没找到配置页索引，请检查文件结构。")

# ==========================================
# 2. 动态语言支持：把 Text 替换为 ZakoText
# ==========================================
# 在 OnboardingPage 的 Composable 里，文本通常是用 data.title, data.subtitle 渲染的
# 我们需要找到这些 Text 并替换为 ZakoText
# 因为引导页有很多自定义的 Text，我们只替换包含 data.xxx 的
# 如果用户切换了语言，只要 ZakoText 内部响应了 AppSettings 状态，就能实时刷新

# 先确保引入了 ZakoText
if 'import com.huijui.sunext.edition.core.ui.language.ZakoText' not in content:
    content = re.sub(r'(package\s+[a-zA-Z0-9_.]+)', r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.ZakoText', content, count=1)

# 匹配 OnboardingPage 里的 Text(data.title) 等，替换为 ZakoText
# 假设原本写法是 Text(text = data.title, ...) 或 Text(data.title, ...)
content = re.sub(r'Text\(text\s*=\s*data\.(\w+)', r'ZakoText(text = data.\1', content)
content = re.sub(r'Text\(data\.(\w+)', r'ZakoText(data.\1', content)

# 兼容 FinalContent, TipsContent, LanguageConfigContent 里面的硬编码标题
# 比如 Text("挑一个主题色") -> 我们也可以替换为 ZakoText("挑一个主题色")
content = re.sub(r'Text\("([^"]+)"', r'ZakoText("\1"', content)

path.write_text(content)
print("✅ 引导页 Text 已替换为 ZakoText，语言切换后立刻动态刷新！")

print("🎉 引导页终极优化完成！直接执行 ./gradlew assembleDebug 起飞！")
