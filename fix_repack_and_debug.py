from pathlib import Path

print("🚀 正在重写 JNI，使用绝对路径修补并确保产出！")

path = Path('core/root/native/shim/jni_wrapper.cpp')

cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>
#include <sys/stat.h>

#include "bootimg.hpp"

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
        
        LOGI("Changing dir to: %s", workDir.c_str());
        if (chdir(workDir.c_str()) != 0) {
            LOGE("Failed to chdir to workDir");
            return JNI_FALSE;
        }

        // 1. 用绝对路径加载原镜像
        std::string srcImgStr(img_in);
        std::string outImgStr(img_out);
        boot_img boot(srcImgStr.c_str());
        
        if (boot.hdr->ramdisk_size() == 0) {
            LOGE("Ramdisk is empty!");
            chdir(oldCwd.c_str());
            return JNI_FALSE;
        }

        // 2. 导出原始 ramdisk
        FILE* fp = fopen("ramdisk.cpio", "wb");
        if (!fp) {
            LOGE("Failed to write ramdisk.cpio");
            chdir(oldCwd.c_str());
            return JNI_FALSE;
        }
        fwrite(boot.ramdisk, 1, boot.hdr->ramdisk_size(), fp);
        fclose(fp);

        // 3. 加载并注入
        magisk_cpio cpio;
        cpio.load_cpio("ramdisk.cpio");
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump("ramdisk.cpio");
        
        // 检查注入后的 cpio 文件
        struct stat st;
        stat("ramdisk.cpio", &st);
        LOGI("Injected ramdisk.cpio size: %ld", st.st_size);

        // 4. 调用 repack (使用绝对路径)
        repack(srcImgStr.c_str(), outImgStr.c_str(), false);
        
        struct stat st_out;
        stat(outImgStr.c_str(), &st_out);
        LOGI("Repacked output size: %ld", st_out.st_size);

        chdir(oldCwd.c_str());
        LOGI("Patch Successful with native repack!");

        env->ReleaseStringUTFChars(imagePath, img_in);
        env->ReleaseStringUTFChars(outputPath, img_out);
        env->ReleaseStringUTFChars(initPath, init_p);
        env->ReleaseStringUTFChars(koPath, ko_p);
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during native repack");
        return JNI_FALSE;
    }
}
'''
path.write_text(cpp_code)
print("✅ JNI 已注入绝杀探测，并确保绝对路径！")
