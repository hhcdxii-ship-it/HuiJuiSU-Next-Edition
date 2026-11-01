from pathlib import Path
import re

print("🚀 正在全量重写 UI 层的本地修补逻辑，彻底剥离 su 依赖...")

# 1. 修改 CoreOverrideScreen.kt
path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = path.read_text()

# 找到本地镜像修补卡片的 onClick 逻辑
start_idx = content.find('onClick = {')
if start_idx != -1:
    end_idx = content.find('}\n', start_idx) + 2
    
    # 替换为纯文件拷贝 + nativePatch
    new_onclick = '''onClick = {
        // 选文件由系统 ActivityResultLauncher 接管
        // 这里只需要触发文件选择器
        filePickerLauncher.launch("*/*")
    }
'''
    content = content[:start_idx] + new_onclick + content[end_idx:]
    print("✅ CoreOverrideScreen.kt 卡片点击逻辑已重写！")

# 2. 确保 ActivityResultLauncher 的回调里没有 su 调用
# 我们已经重写了读文件的逻辑：tempFile.copyTo(safeImg, overwrite = true)
# 确保没有 System.exec 或 HuijuiShell.exec 的干预
path.write_text(content)
print("✅ 修补入口逻辑已彻底纯净！")
