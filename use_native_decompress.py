from pathlib import Path

print("🚀 正在重写 JNI，直接调用底层 decompress 函数...")

path = Path('core/root/native/shim/jni_wrapper.cpp')

cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <android/log.h>

#include "bootimg.hpp"
#include "magiskboot.hpp"
#include "ramdisk.hpp"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "HuiJuiJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HuiJuiJNI", __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
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

        boot_img boot(img_in);
        size_t r_sz = boot.hdr->ramdisk_size();
        uint8_t* r_data = boot.ramdisk;

        // 1. 识别原始格式
        format_t fmt = check_fmt_lg(r_data, r_sz);
        LOGI("Detected format: %d", (int)fmt);

        // 2. 调用底层解压，写入临时文件
        std::string temp_cpio = "ramdisk_uncompressed.cpio";
        int fd = open(temp_cpio.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0644);
        decompress(fmt, fd, r_data, r_sz);
        close(fd);

        // 3. 注入
        magisk_cpio cpio;
        cpio.load_cpio(temp_cpio.c_str());
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump(temp_cpio.c_str());

        // 4. 读取注入后的未压缩 ramdisk
        FILE* fp = fopen(temp_cpio.c_str(), "rb");
        fseek(fp, 0, SEEK_END);
        long new_ramdisk_size = ftell(fp);
        fseek(fp, 0, SEEK_SET);
        void* new_ramdisk = malloc(new_ramdisk_size);
        fread(new_ramdisk, 1, new_ramdisk_size, fp);
        fclose(fp);

        // 5. 拼装镜像
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

        FILE* out_fp = fopen(img_out, "wb");
        fwrite(out_buf, 1, total_size, out_fp);
        fclose(out_fp);

        free(out_buf);
        free(new_ramdisk);
        unlink(temp_cpio.c_str());
        chdir(oldCwd.c_str());

        LOGI("Patch Success! Size: %ld", total_size);
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during patching");
        return JNI_FALSE;
    }
}
'''

path.write_text(cpp_code)
print("✅ JNI 已重写：彻底抛弃手动解压，拥抱底层原生 decompress 函数！")
