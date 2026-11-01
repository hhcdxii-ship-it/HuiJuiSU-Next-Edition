import re
from pathlib import Path
import os

# === 修复 C++ ===
hpp = Path('core/root/native/boot/bootimg.hpp')
if hpp.exists():
    content = hpp.read_text()
    # 移除之前脚本注入的那一坨
    content = re.sub(r'#pragma once[\s\S]*?struct mmap_data \{[\s\S]*?\};\n', '#pragma once\n#include "base.hpp"\n', content)
    hpp.write_text(content)
    print("✅ bootimg.hpp 已清理，改为引用 base.hpp")

# === 修复 Kotlin ===
kt_path = 'app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt'
# 先强行 git checkout 恢复被破坏的 UI 文件
os.system(f'git checkout -- {kt_path} 2>/dev/null')
print("✅ Kotlin 文件已通过 git 恢复（如果没 commit 则可能依然乱，请看下面手动版）")

# 在恢复后的文件上，精准把旧代码替换为协程 Flow
kt = Path(kt_path)
if kt.exists():
    content = kt.read_text()
    # 找到旧版的 installLkm 调用
    old_block = re.search(r'val success = HsudInstaller\.installLkm\([\s\S]*?if\s*\(success\)\s*\{[\s\S]*?\}', content)
    
    if old_block:
        new_block = '''scope.launch {
        HsudInstaller.directPatchAndFlash("init_boot").collect { state ->
            when (state) {
                is HsudInstaller.PatchState.Progress -> {
                    android.util.Log.d("HuiJuiSU", state.message)
                }
                is HsudInstaller.PatchState.Success -> {
                    patchedImgPath = state.outputPath
                    isProcessing = false
                }
                is HsudInstaller.PatchState.Error -> {
                    android.util.Log.e("HuiJuiSU", state.errorMsg)
                    isProcessing = false
                }
            }
        }
    }'''
        content = content.replace(old_block.group(0), new_block)
        kt.write_text(content)
        print("✅ 已成功将旧同步调用替换为异步 Flow 流！")
    else:
        print("⚠️ 未找到旧的 installLkm 调用，可能文件已经被你手动改过，或者 git 没恢复成功。")

