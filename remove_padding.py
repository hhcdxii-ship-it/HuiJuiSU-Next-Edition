from pathlib import Path

print("🚀 正在切除 repack 的强制补 0 逻辑...")

path = Path('core/root/native/boot/bootimg.cpp')
content = path.read_text()

# 找到补 0 的那段代码并注释掉
old_block = '''    // Pad image to original size if not chromeos (as it requires post processing)
    if (!boot.flags[CHROMEOS_FLAG]) {
        off_t current = lseek(fd, 0, SEEK_CUR);
        if (current < boot.map.sz) {
            write_zero(fd, boot.map.sz - current);
        }
    }'''

new_block = '''    // 🔥 纯血爆破：强制切除等长补齐，允许镜像变大以容纳 ko 模块！
    // if (!boot.flags[CHROMEOS_FLAG]) {
    //     off_t current = lseek(fd, 0, SEEK_CUR);
    //     if (current < boot.map.sz) {
    //         write_zero(fd, boot.map.sz - current);
    //     }
    // }'''

if old_block in content:
    content = content.replace(old_block, new_block)
    path.write_text(content)
    print("✅ 强制补 0 逻辑已物理超度！")
else:
    print("⚠️ 没找到原版补 0 逻辑，尝试正则匹配...")
    import re
    content = re.sub(r'// Pad image to original size if not chromeos[\s\S]*?write_zero\(fd, boot\.map\.sz - current\);\s*\}', '// Padding removed', content)
    path.write_text(content)

# 同时，在 jni_wrapper.cpp 里调用 repack 时，强行传入 skip_comp = true，关闭压缩！
cpp_path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp_content = cpp_path.read_text()
cpp_content = cpp_content.replace('repack(srcImgStr.c_str(), outImgStr.c_str(), false);', 'repack(srcImgStr.c_str(), outImgStr.c_str(), true); // 强制不压缩！')
cpp_path.write_text(cpp_content)
print("✅ 底层已强制关闭压缩，输出真体积！")
