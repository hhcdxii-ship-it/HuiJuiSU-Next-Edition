import os
import re
from pathlib import Path

print("🔍 正在修复 UI 层调用断头...")

file = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
if not file.exists():
    print("❌ 找不到 CoreOverrideScreen.kt")
    exit(1)

content = file.read_text()

# 1. 确保导入了协程和 Flow 相关的类
if 'import androidx.compose.runtime.rememberCoroutineScope' not in content:
    content = content.replace('import androidx.compose.runtime.*', 'import androidx.compose.runtime.*\nimport kotlinx.coroutines.launch')

# 2. 注入作用域，用于启动协程
if 'val scope = rememberCoroutineScope()' not in content:
    # 假设函数体开头
    content = re.sub(r'(@Composable\s*fun\s+\w+\([^)]*\)\s*\{)', r'\1\n    val scope = rememberCoroutineScope()', content)

# 3. 匹配并替换旧的 installLkm 调用
# 假设旧代码大概长这样：val success = HsudInstaller.installLkm(...)
pattern = r'val success = HsudInstaller\.installLkm\([^)]*\)'
replacement = """scope.launch {
                                    HsudInstaller.directPatchAndFlash("init_boot").collect { state ->
                                        when (state) {
                                            is HsudInstaller.PatchState.Progress -> {
                                                // 如果你的 UI 有 logText 变量，请改成 logText = state.message
                                                // 这里用 Log 打印，防止你 UI 变量名不一样报错
                                                android.util.Log.d("HuiJuiSU", state.message)
                                            }
                                            is HsudInstaller.PatchState.Success -> {
                                                android.util.Log.d("HuiJuiSU", "成功: ${state.outputPath}")
                                            }
                                            is HsudInstaller.PatchState.Error -> {
                                                android.util.Log.e("HuiJuiSU", "失败: ${state.errorMsg}")
                                            }
                                        }
                                    }
                                }"""

new_content = re.sub(pattern, replacement, content)

if new_content != content:
    file.write_text(new_content)
    print("✅ UI 调用已成功替换为最新的 Flow 异步接口！")
    print("⚠️ 注意：由于改了异步，原本 if (success) 的代码逻辑可能被破坏，你最好手动去第 237 行检查一下是否有多余的括号，或者把上面的 Log 打印换成你 UI 里的文本框变量。")
else:
    print("❌ 没能自动替换，请手动打开第 237 行，把 installLkm 替换成我给的 scope.launch 代码块。")

