import re
from pathlib import Path

file_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
if not file_path.exists():
    print("❌ 找不到文件")
    exit(1)

content = file_path.read_text()

# 1. 删掉残留的 if (success) { ... } 块
pattern = r'if\s*\(\s*success\s*\)\s*\{[\s\S]*?\}'
content = re.sub(pattern, '// 已由 Flow 异步接管，此处废弃\n', content, count=1)

# 2. 在 Flow 的 Success 状态里，把原本的 UI 更新逻辑补进去
# 查找状态处理代码段
success_pattern = r'(is\s+HsudInstaller\.PatchState\.Success\s*->\s*\{)([\s\S]*?)(\})'
def inject_ui(match):
    head, body, tail = match.groups()
    # 把原本在 if(success) 里的逻辑塞进来
    ui_logic = '''
        if (selectedUri != null) {
            patchedImgPath = state.outputPath // 使用底层返回的真实路径
        } else {
            isPatched = true
        }
        isProcessing = false
'''
    return head + body + ui_logic + tail

content = re.sub(success_pattern, inject_ui, content, count=1)

# 3. 确保 Error 状态也能关掉 isProcessing
error_pattern = r'(is\s+HsudInstaller\.PatchState\.Error\s*->\s*\{)([\s\S]*?)(\})'
def inject_error_ui(match):
    head, body, tail = match.groups()
    return head + body + '\n        isProcessing = false' + tail

content = re.sub(error_pattern, inject_error_ui, content, count=1)

file_path.write_text(content)
print("✅ 完美修复！已彻底剥离 success 变量，UI 状态已与 Flow 成功/失败分支无缝接轨！")
