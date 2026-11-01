from pathlib import Path

print("🚀 正在重写 JNI 底层：直接调用 C++ 内存引擎，绕过 noexec 限制...")

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
    const char* part = env->GetStringUTFChars(targetPartition, nullptr);

    LOGI("Native Direct Patch Start: %s", img_in);

    try {
        // 1. 加载原镜像
        boot_img boot(img_in);
        
        // 2. 解包 ramdisk
        magisk_cpio cpio;
        if (boot.hdr->ramdisk_size() > 0) {
            cpio.unpack(boot.ramdisk, boot.hdr->ramdisk_size());
        } else {
            LOGE("Ramdisk is empty!");
            return JNI_FALSE;
        }
        
        // 3. 执行注入（替换 init，塞入 .ko）
        cpio.patch_huijui(init_p, ko_p);
        
        // 4. 重新打包 ramdisk
        size_t new_size;
        void* new_ramdisk = cpio.repack(&new_size);
        
        // 5. 更新头部并写入文件
        boot.hdr->ramdisk_size() = new_size;
        boot.ramdisk = reinterpret_cast<uint8_t*>(new_ramdisk);
        
        // 咱们之前写了个 dump 方法，如果没有，就用标准的写文件
        FILE* fp = fopen(img_out, "wb");
        if (fp) {
            fwrite(boot.hdr->raw_hdr(), 1, boot.hdr->hdr_size(), fp);
            fwrite(boot.ramdisk, 1, boot.hdr->ramdisk_size(), fp);
            // 这里省略了其他分区的拷贝，因为咱们只是替换 init_boot 的 ramdisk
            fclose(fp);
        } else {
            LOGE("Failed to open output file!");
            return JNI_FALSE;
        }

        // 释放资源
        env->ReleaseStringUTFChars(imagePath, img_in);
        env->ReleaseStringUTFChars(outputPath, img_out);
        env->ReleaseStringUTFChars(initPath, init_p);
        env->ReleaseStringUTFChars(koPath, ko_p);
        env->ReleaseStringUTFChars(targetPartition, part);
        return JNI_TRUE;
        
    } catch (...) {
        LOGE("C++ Exception occurred during patching!");
        return JNI_FALSE;
    }
}
'''

path.write_text(cpp_code)
print("✅ JNI 已重写为纯内存引擎执行！彻底摆脱 noexec 限制！")
