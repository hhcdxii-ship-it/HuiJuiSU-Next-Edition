import re
from pathlib import Path

print("🚀 正在把【语言设置】强制顶到引导页的第一页...")

path = Path('app/src/main/java/com/huijui/sunext/edition/feature/onboarding/OnboardingScreen.kt')
content = path.read_text()

# 1. 精准提取 CONFIG_LANGUAGE 的整段代码块
lang_pattern = r'(OnboardingPageData\(PageType\.CONFIG_LANGUAGE[\s\S]*?\),)'
lang_match = re.search(lang_pattern, content)

if not lang_match:
    print("❌ 找不到语言设置代码块，请检查文件。")
    exit(1)

lang_block = lang_match.group(1)

# 把它的编号改成 STEP 1
lang_block = lang_block.replace('STEP 4 / 4', 'STEP 1 / 3').replace('STEP 2 / 4', 'STEP 1 / 3').replace('STEP 3 / 4', 'STEP 1 / 3')

# 2. 从原位置删除这段代码
content = content.replace(lang_block, '')

# 3. 找到 listOf 的开头，把语言块插进去
# 匹配 val onboardingPages = listOf(
list_start_pattern = r'(val\s+onboardingPages\s*=\s*listOf\()'
match = re.search(list_start_pattern, content)

if match:
    # 在 listOf( 后面直接插入我们的语言块
    content = re.sub(list_start_pattern, r'\1\n    ' + lang_block, content)
    
    # 顺手把后面剩下的 STEP 编号重排一下（刚才已经把语言提出来了，现在原位置应该是空的）
    # 把所有的 STEP 2 / 4 改为 STEP 2 / 3，STEP 3 / 4 改为 STEP 3 / 3
    content = content.replace('STEP 2 / 4', 'STEP 2 / 3')
    content = content.replace('STEP 3 / 4', 'STEP 3 / 3')

    path.write_text(content)
    print("✅ 成功！语言设置已登顶第一页！")
else:
    print("⚠️ 没找到 val onboardingPages = listOf 定义。")
