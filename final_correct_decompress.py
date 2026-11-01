from pathlib import Path

print("🚀 正在重写 JNI，使用文件路径调用底层 decompress...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 1. 删掉之前瞎加的 extern 声明，因为 compress.hpp 里已经有了
cpp = cpp.replace('extern void decompress(format_t fmt, int fd, void *in, size_t in_size);\n\n', '')

# 2. 确保引入 compress.hpp
if '#include "compress.hpp"' not in cpp:
    cpp = cpp.replace('#include "magiskboot.hpp"', '#include "magiskboot.hpp"\n#include "compress.hpp"')

# 3. 重写整个 nativePatch 函数体
start_idx = cpp.find('extern "C" JNIEXPORT jboolean JNICALL')
if start_idx == -1:
    print("❌ 找不到 nativePatch 起始位置！")
    exit(1)

new_code = '''extern "C" JNIEXPORT jboolean JNICALL
Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(
        JNIEnv* env, jobject, jstring imagePath, jstring targetPartition, jstring outputPath, jstring initPath, jstring koPath) {

    const char* img_in = env->GetStringUTFChars(imagePath, nullptr);
    const char* img_out = env->GetStringUTFChars(outputPath, nullptr);
    const char* init_p = env->GetStringUTFChars(initPath, nullptr);
    const char* ko_p = env->GetStringUTFChars(koPath, nullptr);

    LOGI("Native Patch Start: %s", img_in);

    try {
        std::string initPathStr(init_p);
        std::string workDir = initPathStr.substr(0, initPathStr.find_last_of("/"));
        std::string oldCwd = getcwd(nullptr, 0);
        chdir(workDir.c_str());

        // 1. 加载原镜像
        boot_img boot(img_in);
        size_t r_sz = boot.hdr->ramdisk_size();
        uint8_t* r_data = boot.ramdisk;
        
        // 2. 保存压缩的 ramdisk 到临时文件
        std::string temp_compressed = "temp_r_compressed.bin";
        FILE* fp_in = fopen(temp_compressed.c_str(), "wb");
        fwrite(r_data, 1, r_sz, fp_in);
        fclose(fp_in);

        // 3. 调用底层解压（需要绝对路径，或者当前目录下的相对路径）
        std::string temp_uncompressed = "temp_r_uncompressed.cpio";
        char abs_comp[1024], abs_uncomp[1024];
        snprintf(abs_comp, sizeof(abs_comp), "%s/%s", workDir.c_str(), temp_compressed.c_str());
        snprintf(abs_uncomp, sizeof(abs_uncomp), "%s/%s", workDir.c_str(), temp_uncompressed.c_str());
        
        decompress(abs_comp, abs_uncomp);
        LOGI("Decompress completed: %s -> %s", abs_comp, abs_uncomp);

        // 4. 加载未压缩的 cpio 并注入
        magisk_cpio cpio;
        cpio.load_cpio(temp_uncompressed.c_str());
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump(temp_uncompressed.c_str());

        // 5. 读取注入后的 ramdisk
        FILE* fp = fopen(temp_uncompressed.c_str(), "rb");
        fseek(fp, 0, SEEK_END);
        long new_ramdisk_size = ftell(fp);
        fseek(fp, 0, SEEK_SET);
        void* new_ramdisk = malloc(new_ramdisk_size);
        fread(new_ramdisk, 1, new_ramdisk_size, fp);
        fclose(fp);

        // 6. 拼装镜像
        size_t hdr_size = boot.hdr->hdr_size();
        size_t kernel_size = boot.hdr->kernel_size();
        size_t total_size = hdr_size + kernel_size + new_ramdisk_size;
        total_size = (total_size + 4095) & ~4095;

        uint8_t* out_buf = (uint8_t*)calloc(1, total_size);
        memcpy(out_buf, boot.hdr_addr, hdr_size);
        
        boot.hdr->ramdisk_size() = new_ramdisk_size;
        memcpy(out_buf, boot.hdr->raw_hdr(), hdr_size);

        if (kernel_size > 0) memcpy(out_buf + hdr_size, boot.kernel, kernel_size);
        memcpy(out_buf + hdr_size + kernel_size, new_ramdisk, new_ramdisk_size);

        // 7. 写入最终文件
        FILE* out_fp = fopen(img_out, "wb");
        fwrite(out_buf, 1, total_size, out_fp);
        fclose(out_fp);

        // 清理
        free(out_buf);
        free(new_ramdisk);
        unlink(abs_comp);
        unlink(abs_uncomp);
        
        chdir(oldCwd.c_str());

        LOGI("Patch Success! Size: %ld", total_size);
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during patching");
        return JNI_FALSE;
    }
}
'''

cpp = cpp[:start_idx] + new_code
path.write_text(cpp)
print("✅ JNI 已完全重写：调用底层 decompress 处理真实文件！")
