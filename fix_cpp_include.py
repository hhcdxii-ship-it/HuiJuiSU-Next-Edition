from pathlib import Path

print("🚀 正在修复 C++ 头文件缺失...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 确保引入了 compress.hpp 和 magiskboot.hpp
if '#include "compress.hpp"' not in cpp:
    cpp = cpp.replace('#include "magiskboot.hpp"', '#include "magiskboot.hpp"\n#include "compress.hpp"')
if '#include "ramdisk.hpp"' not in cpp:
    cpp = cpp.replace('#include "compress.hpp"', '#include "compress.hpp"\n#include "ramdisk.hpp"')

path.write_text(cpp)
print("✅ 头文件已补齐！")

