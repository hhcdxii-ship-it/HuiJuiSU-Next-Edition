import re
from pathlib import Path

file_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = file_path.read_text()

# 1. 修复 onClick 的大括号
content = re.sub(r'onClick\s*=\s*isProcessing\s*=\s*true', 'onClick = { isProcessing = true }', content)
content = re.sub(r'onClick\s*=\s*isProcessing\s*=\s*false', 'onClick = { isProcessing = false }', content)

# 2. 补齐可能被破坏的 selectedPartition 变量
if 'val selectedPartition' not in content:
    content = re.sub(r'(var isProcessing by remember \{ mutableStateOf\(false\) \})', r'\1\n    var selectedPartition by remember { mutableStateOf("init_boot") }', content)

# 3. 修复 Flow 调用的 Scope 协程
content = re.sub(r'scope\.launch\s*\{\s*HsudInstaller\.directPatchAndFlash\(\)', 'scope.launch { HsudInstaller.directPatchAndFlash(selectedPartition)', content)

# 把之前可能没传参数的改对
content = re.sub(r'HsudInstaller\.directPatchAndFlash\(\)', 'HsudInstaller.directPatchAndFlash(selectedPartition)', content)

file_path.write_text(content)
print("✅ Kotlin 语法与变量已修复！")
