from pathlib import Path

print("🚀 正在给 C++ 注入终极排雷探针...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 解压节点探针
cpp = cpp.replace(
    'decompress(abs_comp, abs_uncomp);',
    '''decompress(abs_comp, abs_uncomp);
        FILE* f_dcp = fopen("/sdcard/Download/cpp_probe_decompress.txt", "w");
        if (f_dcp) { fprintf(f_dcp, "Decompress finished.\\n"); fclose(f_dcp); }'''
)

# 加载 cpio 节点探针
cpp = cpp.replace(
    'cpio.load_cpio(temp_uncompressed.c_str());',
    '''cpio.load_cpio(temp_uncompressed.c_str());
        FILE* f_load = fopen("/sdcard/Download/cpp_probe_loadcpio.txt", "w");
        if (f_load) { fprintf(f_load, "CPIO Loaded. Init exists: %s\\n", cpio.exists("init") ? "true" : "false"); fclose(f_load); }'''
)

# 注入模块节点探针
cpp = cpp.replace(
    'cpio.patch_huijui(init_p, ko_p);',
    '''cpio.patch_huijui(init_p, ko_p);
        FILE* f_patch = fopen("/sdcard/Download/cpp_probe_patch.txt", "w");
        if (f_patch) { fprintf(f_patch, "Patch applied. Init exists: %s\\n", cpio.exists("init") ? "true" : "false"); fclose(f_patch); }'''
)

path.write_text(cpp)
print("✅ 探针注入完成！")
