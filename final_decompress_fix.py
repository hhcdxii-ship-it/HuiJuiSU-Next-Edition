from pathlib import Path

print("🚀 正在修复底层函数名并强制引入解压模块...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 1. 修复 check_fmt_lg 为 check_fmt
cpp = cpp.replace('check_fmt_lg', 'check_fmt')

# 2. 强制引入 compress 头文件，或手动声明 decompress
# 通常 Magisk 的 decompress 声明在 compress.hpp
if '#include "compress.hpp"' not in cpp:
    cpp = cpp.replace('#include "magiskboot.hpp"', '#include "magiskboot.hpp"\n#include "compress.hpp"')

# 3. 如果底层真把 decompress 藏起来了，我们直接暴力声明它！
# 根据 bootimg.cpp 里的调用：decompress(boot.r_fmt, fd, boot.ramdisk, boot.hdr->ramdisk_size());
if 'void decompress(' not in cpp:
    # 在 includes 之后，手动 extern 声明
    cpp = cpp.replace('extern "C" JNIEXPORT', 'extern void decompress(format_t fmt, int fd, void *in, size_t in_size);\n\nextern "C" JNIEXPORT')

path.write_text(cpp)
print("✅ 修复完毕！如果 compress.hpp 存在就能直接过，如果不存在，靠 extern 强制链接！")
