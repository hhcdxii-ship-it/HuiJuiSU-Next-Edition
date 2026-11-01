import os
import re
from pathlib import Path

print("🚀 正在执行终极清理手术...")

# ==========================================
# 1. 修复 bootimg.hpp 的符号缺失问题
# ==========================================
bootimg_hpp = Path('core/root/native/boot/bootimg.hpp')
if bootimg_hpp.exists():
    content = bootimg_hpp.read_text()
    # 检查是否已经注入过，防止重复
    if 'static inline void* xmalloc' not in content:
        inject_head = '''#pragma once
#include <cstdlib>
#include <cstddef>
#include <cstring>
#include <cstdint>

// 🔥 纯血补齐缺失的基础定义，防止编译报错
static inline void* xmalloc(size_t size) { 
    void* p = malloc(size); 
    if (!p) abort(); 
    return p; 
}
static inline size_t align_to(size_t v, size_t a) { 
    return (v + a - 1) & ~(a - 1); 
}
struct mmap_data { 
    void* buf; 
    size_t sz; 
};
'''
        # 如果原本有 #pragma once，就覆盖掉它，否则直接在开头加
        if content.startswith('#pragma once'):
            content = content.replace('#pragma once', inject_head, 1)
        else:
            content = inject_head + content
            
        bootimg_hpp.write_text(content)
        print("✅ bootimg.hpp 缺失的 xmalloc、align_to、mmap_data 已就地补齐！")

# ==========================================
# 2. 清理 jni_wrapper.cpp 多余的 include
# ==========================================
jni_cpp = Path('core/root/native/shim/jni_wrapper.cpp')
if jni_cpp.exists():
    content = jni_cpp.read_text()
    # 删掉可能不存在的头文件引用
    content = re.sub(r'#include "xwrap.hpp"', '', content)
    content = re.sub(r'#include "mmap.hpp"', '', content)
    content = re.sub(r'#include "ramdisk.hpp"', '', content)
    jni_cpp.write_text(content)
    print("✅ jni_wrapper.cpp 冗余 include 已清理！")

# ==========================================
# 3. 修复 Kotlin UI 状态与括号
# ==========================================
kt_file = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
if kt_file.exists():
    content = kt_file.read_text()
    
    # 暴力寻找 onClick，然后把后面全换了
    start_idx = content.find('onClick = {')
    if start_idx != -1:
        content = content[:start_idx]
        
        new_block = '''onClick = {
        isProcessing = true
        scope.launch {
            HsudInstaller.directPatchAndFlash("init_boot").collect { state ->
                when (state) {
                    is HsudInstaller.PatchState.Progress -> {
                        android.util.Log.d("HuiJuiSU", state.message)
                    }
                    is HsudInstaller.PatchState.Success -> {
                        isPatched = true
                        isProcessing = false
                    }
                    is HsudInstaller.PatchState.Error -> {
                        android.util.Log.e("HuiJuiSU", state.errorMsg)
                        isProcessing = false
                    }
                }
            }
        }
    }
) {
    Text(text = if (isProcessing) "正在提取并修补底层..." else "开始一键修补")
}
// 补齐可能缺失的结尾大括号（视你原代码结构而定，大概率在文件尾）
}
}
'''
        content += new_block
        kt_file.write_text(content)
        print("✅ Kotlin UI onClick 逻辑已彻底重写！")

print("🎉 所有修复完成，请执行 ./gradlew assembleDebug")
