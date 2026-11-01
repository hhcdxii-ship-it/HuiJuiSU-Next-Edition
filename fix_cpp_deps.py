from pathlib import Path
import re

print("🚀 正在修复 C++ 头文件依赖与重复定义...")

# 1. 清理 bootimg.hpp 里我们瞎塞的重复声明
hpp_path = Path('core/root/native/boot/bootimg.hpp')
content = hpp_path.read_text()
content = content.replace('void repack(const char *src_img, const char *out_img, bool skip_comp = false);\n\n', '')
hpp_path.write_text(content)
print("✅ bootimg.hpp 重复声明已清理！")

# 2. 修复 jni_wrapper.cpp 的头文件引入
cpp_path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp_content = cpp_path.read_text()

# 确保引入 magiskboot.hpp (提供 repack) 和 ramdisk.hpp (提供 magisk_cpio)
if '#include "magiskboot.hpp"' not in cpp_content:
    cpp_content = cpp_content.replace('#include "bootimg.hpp"', '#include "bootimg.hpp"\n#include "magiskboot.hpp"\n#include "ramdisk.hpp"')

cpp_path.write_text(cpp_content)
print("✅ jni_wrapper.cpp 已补齐所有底层头文件！")
