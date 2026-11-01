from pathlib import Path

print("🚀 正在注入底层体积拦截器...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 在读取 ramdisk 之后插入强制校验
cpp = cpp.replace(
    'fseek(fp, 0, SEEK_SET);\n        void* new_ramdisk = malloc(new_ramdisk_size);',
    '''fseek(fp, 0, SEEK_SET);
        // 🔥 纯血拦截：如果注入后的 ramdisk 小于 1MB，绝对是解析失败或空壳，直接原地引爆！
        if (new_ramdisk_size < 1000000) {
            LOGE("Ramdisk size too small: %ld", new_ramdisk_size);
            FILE* probe_err = fopen("/sdcard/Download/cpp_probe_error.txt", "w");
            if (probe_err) { fprintf(probe_err, "Ramdisk too small: %ld bytes\\n", new_ramdisk_size); fclose(probe_err); }
            fclose(fp);
            return JNI_FALSE;
        }
        void* new_ramdisk = malloc(new_ramdisk_size);'''
)
path.write_text(cpp)
print("✅ 强制校验拦截器已注入！")
