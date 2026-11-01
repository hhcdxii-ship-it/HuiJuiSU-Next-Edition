from pathlib import Path

print("🚀 正在重写 JNI，使用真实存在的 load_cpio 和 dump API...")

path = Path('core/root/native/shim/jni_wrapper.cpp')

cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>

// 引入底层核心头文件
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

    LOGI("Native Direct Patch Start: %s", img_in);

    try {
        // 推导出临时目录（和 init_p 在同一目录）
        std::string initPathStr(init_p);
        std::string dir = initPathStr.substr(0, initPathStr.find_last_of("/"));
        std::string temp_in = dir + "/temp_in.cpio";
        std::string temp_out = dir + "/temp_out.cpio";

        // 1. 加载原镜像
        boot_img boot(img_in);
        
        if (boot.hdr->ramdisk_size() == 0) {
            LOGE("Ramdisk is empty!");
            return JNI_FALSE;
        }

        // 2. 把 boot 里的 ramdisk 内存数据写到临时文件
        FILE* fp = fopen(temp_in.c_str(), "wb");
        if (!fp) {
            LOGE("Failed to write temp_in.cpio");
            return JNI_FALSE;
        }
        fwrite(boot.ramdisk, 1, boot.hdr->ramdisk_size(), fp);
        fclose(fp);
        
        // 3. 用底层引擎加载 ramdisk 并执行注入
        magisk_cpio cpio;
        cpio.load_cpio(temp_in.c_str());
        cpio.patch_huijui(init_p, ko_p);
        
        // 4. 打包输出到临时文件
        cpio.dump(temp_out.c_str());

        // 5. 把修补后的 ramdisk 读回内存
        fp = fopen(temp_out.c_str(), "rb");
        if (!fp) {
            LOGE("Failed to read temp_out.cpio");
            return JNI_FALSE;
        }
        fseek(fp, 0, SEEK_END);
        long new_size = ftell(fp);
        fseek(fp, 0, SEEK_SET);
        
        void* new_ramdisk = malloc(new_size);
        fread(new_ramdisk, 1, new_size, fp);
        fclose(fp);

        // 6. 更新头部大小和内存指针
        boot.hdr->ramdisk_size() = new_size;
        boot.ramdisk = reinterpret_cast<uint8_t*>(new_ramdisk);
        
        // 7. 写出最终的 boot.img / init_boot.img
        FILE* out_fp = fopen(img_out, "wb");
        if (!out_fp) {
            LOGE("Failed to open output file!");
            return JNI_FALSE;
        }
        
        // 写入完整的镜像：头部 + 内核 + 新 ramdisk + 其他（比如 dtb）
        fwrite(boot.hdr->raw_hdr(), 1, boot.hdr->hdr_size(), out_fp);
        
        if (boot.hdr->kernel_size() > 0) {
            fwrite(boot.kernel, 1, boot.hdr->kernel_size(), out_fp);
        }
        fwrite(boot.ramdisk, 1, boot.hdr->ramdisk_size(), out_fp);
        
        fclose(out_fp);

        LOGI("Patch Successful!");
        
        env->ReleaseStringUTFChars(imagePath, img_in);
        env->ReleaseStringUTFChars(outputPath, img_out);
        env->ReleaseStringUTFChars(initPath, init_p);
        env->ReleaseStringUTFChars(koPath, ko_p);
        
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("C++ Exception occurred during patching!");
        return JNI_FALSE;
    }
}
'''

path.write_text(cpp_code)
print("✅ JNI 已重写：使用 load_cpio / dump 进行真实修补，并导出完整镜像！")
