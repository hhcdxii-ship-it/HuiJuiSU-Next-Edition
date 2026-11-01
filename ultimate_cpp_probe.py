from pathlib import Path

print("🚀 正在注入终极底层探针，强制记录解压过程...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 1. 在解压前写入原厂大小和格式
cpp = cpp.replace(
    '// 2. 检测解压格式',
    '''// 2. 检测解压格式
        FILE* probe_size = fopen("/sdcard/Download/cpp_probe_size.txt", "w");
        if (probe_size) { fprintf(probe_size, "Original ramdisk size: %zu\\nFormat: %d\\n", r_sz, (int)boot.r_fmt); fclose(probe_size); }
'''
)

# 2. 在 load_cpio 之后，强制打印解压后的大小
cpp = cpp.replace(
    'cpio.load_cpio(temp_cpio.c_str());',
    '''cpio.load_cpio(temp_cpio.c_str());
        FILE* probe_cpio = fopen("/sdcard/Download/cpp_probe_cpio.txt", "w");
        if (probe_cpio) { fprintf(probe_cpio, "Entries loaded: %zu\\n", cpio.entries.size()); fclose(probe_cpio); }'''
)

# 3. 确保之前的拦截器真正生效（强行拉起）
cpp = cpp.replace(
    'long new_ramdisk_size = ftell(fp);',
    '''long new_ramdisk_size = ftell(fp);
        FILE* probe_new = fopen("/sdcard/Download/cpp_probe_new.txt", "w");
        if (probe_new) { fprintf(probe_new, "New ramdisk size: %ld\\n", new_ramdisk_size); fclose(probe_new); }'''
)

path.write_text(cpp)
print("✅ 探针注入完毕！如果这次还是空壳，文件里会告诉我们原因！")
