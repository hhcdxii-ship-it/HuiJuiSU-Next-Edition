from pathlib import Path

print("🚀 正在重写 JNI，手工拼装完整镜像...")

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

    LOGI("Manual Assemble Start: %s", img_in);

    try {
        std::string initPathStr(init_p);
        std::string workDir = initPathStr.substr(0, initPathStr.find_last_of("/"));
        std::string oldCwd = getcwd(nullptr, 0);
        chdir(workDir.c_str());

        // 1. 导出原始 ramdisk
        boot_img boot(img_in);
        FILE* fp = fopen("ramdisk.cpio", "wb");
        fwrite(boot.ramdisk, 1, boot.hdr->ramdisk_size(), fp);
        fclose(fp);

        // 2. 注入
        magisk_cpio cpio;
        cpio.load_cpio("ramdisk.cpio");
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump("ramdisk.cpio");

        // 3. 读取注入后的 ramdisk
        fp = fopen("ramdisk.cpio", "rb");
        fseek(fp, 0, SEEK_END);
        long new_ramdisk_size = ftell(fp);
        fseek(fp, 0, SEEK_SET);
        void* new_ramdisk = malloc(new_ramdisk_size);
        fread(new_ramdisk, 1, new_ramdisk_size, fp);
        fclose(fp);

        // 4. 手动拼装镜像
        size_t hdr_size = boot.hdr->hdr_size();
        size_t kernel_size = boot.hdr->kernel_size();
        size_t total_size = hdr_size + kernel_size + new_ramdisk_size;
        // 对齐到 4096
        total_size = (total_size + 4095) & ~4095;

        uint8_t* out_buf = (uint8_t*)calloc(1, total_size);
        
        // 拷贝头部
        memcpy(out_buf, boot.hdr_addr, hdr_size);
        
        // 更新头部中的 ramdisk_size
        boot.hdr->ramdisk_size() = new_ramdisk_size;
        memcpy(out_buf, boot.hdr->raw_hdr(), hdr_size);

        // 拷贝内核
        if (kernel_size > 0) {
            memcpy(out_buf + hdr_size, boot.kernel, kernel_size);
        }

        // 拷贝新 ramdisk
        memcpy(out_buf + hdr_size + kernel_size, new_ramdisk, new_ramdisk_size);

        // 5. 写入最终文件
        FILE* out_fp = fopen(img_out, "wb");
        fwrite(out_buf, 1, total_size, out_fp);
        fclose(out_fp);

        free(out_buf);
        free(new_ramdisk);
        chdir(oldCwd.c_str());

        LOGI("Manual Assemble Success! Size: %ld", total_size);
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during manual assemble");
        return JNI_FALSE;
    }
}
'''
path.write_text(cpp_code)
print("✅ JNI 已重写为纯手工精准拼装！")
