import re
from pathlib import Path

file_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = file_path.read_text()

# 匹配从 scope.launch 开始，到它后面的逻辑，全部强制覆写
start_idx = content.find('scope.launch {')
if start_idx != -1:
    # 找到要替换的结束位置（大概在原本的 if (success) 之后）
    end_idx = content.find('isProcessing = false', start_idx) + len('isProcessing = false')
    
    new_launch_block = '''scope.launch {
        HsudInstaller.directPatchAndFlash(selectedPartition).collect { state ->
            when (state) {
                is HsudInstaller.PatchState.Progress -> {
                    android.util.Log.d("HuiJuiSU", state.message)
                }
                is HsudInstaller.PatchState.Success -> {
                    if (selectedUri != null) {
                        patchedImgPath = state.outputPath
                    } else {
                        isPatched = true
                    }
                    isProcessing = false
                }
                is HsudInstaller.PatchState.Error -> {
                    android.util.Log.e("HuiJuiSU", state.errorMsg)
                    isProcessing = false
                }
            }
        }
    }'''
    content = content[:start_idx] + new_launch_block + content[end_idx:]
    file_path.write_text(content)
    print("✅ Kotlin 语法已重写！括号已闭合，isProcessing 归位！")
else:
    print("❌ 没找到 scope.launch，请手动检查 CoreOverrideScreen.kt")
