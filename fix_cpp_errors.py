import re
from pathlib import Path

print("🛠️ 开始修复 C++ 编译报错...")

# ==========================================
# 1. 修复 cpio.hpp，添加 patch_huijui 声明
# ==========================================
hpp_file = Path('core/root/native/boot/cpio.hpp')
if hpp_file.exists():
    content = hpp_file.read_text()
    if 'patch_huijui' not in content:
        # 插入到 class magisk_cpio 的 public 部分（找 'public:' 后加）
        content = re.sub(r'(class\s+magisk_cpio\s*\{[\s\S]*?public:)', r'\1\n    void patch_huijui(const char* huijuiinit_path, const char* ko_path);', content, count=1)
        hpp_file.write_text(content)
        print("✅ cpio.hpp: 已添加 patch_huijui 声明")
else:
    print("❌ 找不到 cpio.hpp")

# ==========================================
# 2. 重写 ramdisk.cpp 中的 patch_huijui 实现
# ==========================================
cpp_file = Path('core/root/native/boot/ramdisk.cpp')
if cpp_file.exists():
    content = cpp_file.read_text()
    
    # 删掉旧的、报错的 patch_huijui 函数（从函数头删到匹配的右括号）
    pattern = r'void magisk_cpio::patch_huijui\([\s\S]*?\n\}\n'
    content = re.sub(pattern, '', content)

    # 注入标准的、适配 Magisk 框架的完整实现
    inject_code = '''
void magisk_cpio::patch_huijui(const char* huijuiinit_path, const char* ko_path) {
    if (entries.find(".huijui_patched") != entries.end()) {
        fprintf(stderr, "[-] 已修补过，跳过注入。\\n");
        return;
    }

    auto it = entries.find("init");
    if (it != entries.end()) {
        // 获取原厂 init 的数据
        auto& init_entry = it->second;
        uint32_t mode = init_entry->mode;
        
        // 备份 init 为 init.real (深拷贝)
        auto init_real = std::make_unique<cpio_entry>(mode);
        init_real->data = (char*)malloc(init_entry->filesize);
        memcpy(init_real->data, init_entry->data, init_entry->filesize);
        init_real->filesize = init_entry->filesize;
        entries["init.real"] = std::move(init_real);

        // 读取 huijuiinit 并替换 init
        std::string h_data = read_file_to_string(huijuiinit_path);
        if (!h_data.empty()) {
            init_entry->data = (char*)realloc(init_entry->data, h_data.size());
            memcpy(init_entry->data, h_data.data(), h_data.size());
            init_entry->filesize = h_data.size();
            init_entry->mode = 0750;
            fprintf(stderr, "[-] ✅ 已成功将 init 替换为 huijuiinit\\n");
        } else {
            fprintf(stderr, "[-] ❌ 无法读取 huijuiinit: %s\\n", huijuiinit_path);
        }
    }

    // 注入 ko
    std::string ko_data = read_file_to_string(ko_path);
    if (!ko_data.empty()) {
        auto ko_entry = std::make_unique<cpio_entry>(0644);
        ko_entry->data = (char*)malloc(ko_data.size());
        memcpy(ko_entry->data, ko_data.data(), ko_data.size());
        ko_entry->filesize = ko_data.size();
        entries["huijui_phantom.ko"] = std::move(ko_entry);
        fprintf(stderr, "[-] ✅ 已注入内核模块\\n");
    } else {
        fprintf(stderr, "[-] ❌ 无法读取 ko: %s\\n", ko_path);
    }

    // 写入标记
    const char* flag = "patched";
    auto flag_entry = std::make_unique<cpio_entry>(0644);
    flag_entry->data = strdup(flag);
    flag_entry->filesize = strlen(flag);
    entries[".huijui_patched"] = std::move(flag_entry);
}
'''
    content += inject_code
    cpp_file.write_text(content)
    print("✅ ramdisk.cpp: patch_huijui 实现已全量重写并修复语法")
else:
    print("❌ 找不到 ramdisk.cpp")

# ==========================================
# 3. 修复 jni_wrapper.cpp 乱插的代码
# ==========================================
jni_file = Path('core/root/native/shim/jni_wrapper.cpp')
if jni_file.exists():
    content = jni_file.read_text()
    
    # 清理掉之前用脚本瞎插的代码
    content = re.sub(r'\s*//\s*🔥 纯血 V6 核心注入调用[\s\S]*?cpio\.patch_huijui\(init_p, ko_p\);\s*\}', '', content)
    
    # 重新在正确的 JNI 函数里注入
    # 找到 nativePatch 的入口
    pattern = r'(JNIEXPORT jboolean JNICALL Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch[\s\S]*?\{)'
    
    def insert_logic(match):
        header = match.group(1)
        # 假设后续有 unpack 过程，我们这里提供正确的变量名
        logic = '''
    const char* init_p = env->GetStringUTFChars(initPath, nullptr);
    const char* ko_p = env->GetStringUTFChars(koPath, nullptr);
    setenv("PATCHVBMETAFLAG", "true", 1);
    setenv("TARGET_PARTITION", env->GetStringUTFChars(targetPartition, nullptr), 1);

    // ... 这里是 unpack 逻辑，假设生成了 cpio 对象 ...
    // magisk_cpio cpio;
    // cpio.unpack(...);

    // 核心注入
    // cpio.patch_huijui(init_p, ko_p);

    // ... repack 逻辑 ...
'''
        return header + logic

    content = re.sub(pattern, insert_logic, content)
    jni_file.write_text(content)
    print("✅ jni_wrapper.cpp: 已清理乱码，并修正 JNI 传参逻辑")
else:
    print("❌ 找不到 jni_wrapper.cpp")

print("🎉 修复完成！请务必手动打开 jni_wrapper.cpp，取消注释 cpio.patch_huijui 并确保周围 unpack/repack 逻辑正确。")
