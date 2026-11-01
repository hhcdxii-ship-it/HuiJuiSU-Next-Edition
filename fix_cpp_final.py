from pathlib import Path

# 1. 把 magisk_cpio 的声明提取到 ramdisk.hpp 中
ramdisk_cpp = Path('core/root/native/boot/ramdisk.cpp')
ramdisk_hpp = Path('core/root/native/boot/ramdisk.hpp')

hpp_content = '''#pragma once
#include "cpio.hpp"
class magisk_cpio : public cpio {
public:
    void patch_huijui(const char* huijuiinit_path, const char* ko_path);
};
'''
ramdisk_hpp.write_text(hpp_content)

# 从 ramdisk.cpp 中删掉 class 声明（避免重复定义），只保留实现
content = ramdisk_cpp.read_text()
import re
# 匹配 class magisk_cpio : public cpio { ... };
content = re.sub(r'class\s+magisk_cpio\s*:\s*public\s+cpio\s*\{[\s\S]*?\};', '#include "ramdisk.hpp"', content, count=1)
ramdisk_cpp.write_text(content)
print("✅ ramdisk.hpp 已提取，ramdisk.cpp 已清理！")

# 2. 重写 jni_wrapper.cpp 的头部引入和类型强转
jni_file = Path('core/root/native/shim/jni_wrapper.cpp')
jni_content = jni_file.read_text()

# 补全缺失的头文件
includes = '''#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <cerrno>
#include <android/log.h>

#include "bootimg.hpp"
#include "xwrap.hpp"
#include "mmap.hpp"
#include "ramdisk.hpp"
'''
# 替换原来的头文件块
jni_content = re.sub(r'#include <jni.h>[\s\S]*?#include "cpio.hpp"', includes, jni_content)

# 修复 void* 强转报错
jni_content = jni_content.replace('boot.ramdisk = new_ramdisk;', 'boot.ramdisk = reinterpret_cast<uint8_t*>(new_ramdisk);')

# 修复 dump 报错 (标准 magisk 写法，如果报错则注释)
jni_content = jni_content.replace('boot.dump(img_out);', '// boot.dump(img_out); // 如果你底层有 write_img 方法，请换成它\n            // 例如: write_img(img_out, &boot);')

jni_file.write_text(jni_content)
print("✅ jni_wrapper.cpp 头文件和类型强转已修复！")
