from pathlib import Path
import re

print("🚀 正在注入物理探针，强制让 C++ 留下痕迹...")

cpp_path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp_content = cpp_path.read_text()

# 在 try 块开头加上文件写入探针
cpp_content = cpp_content.replace(
    'try {\n        std::string initPathStr(init_p);',
    '''try {
        FILE* probe = fopen("/sdcard/Download/cpp_probe_start.txt", "w");
        if (probe) { fprintf(probe, "C++ Started!\\n"); fclose(probe); }
        
        std::string initPathStr(init_p);'''
)

# 在 repack 之前写第二个探针
cpp_content = cpp_content.replace(
    '// 5. 调用底层原生的 repack 函数',
    '''FILE* probe2 = fopen("/sdcard/Download/cpp_probe_ramdisk.txt", "w");
        if (probe2) { fprintf(probe2, "Ramdisk Injected! Size: %ld\\n", st.st_size); fclose(probe2); }

        // 5. 调用底层原生的 repack 函数'''
)

# 在成功返回前写第三个探针
cpp_content = cpp_content.replace(
    'LOGI("Patch Successful with native repack!");',
    '''LOGI("Patch Successful with native repack!");
        FILE* probe3 = fopen("/sdcard/Download/cpp_probe_success.txt", "w");
        if (probe3) { fprintf(probe3, "Repack Success! Size: %ld\\n", st_out.st_size); fclose(probe3); }'''
)

cpp_path.write_text(cpp_content)
print("✅ C++ 探针注入完毕！现在它必定会在 Download 目录留下痕迹！")
