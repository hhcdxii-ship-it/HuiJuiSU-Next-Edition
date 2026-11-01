from pathlib import Path

print("🚀 正在重写 JNI，改用底层的全局 repack 逻辑...")

# 1. 确保 bootimg.hpp 里暴露了 repack 函数声明
hpp_path = Path('core/root/native/boot/bootimg.hpp')
content = hpp_path.read_text()
if 'void repack(' not in content:
    content = content.replace('struct boot_img {', 'void repack(const char *src_img, const char *out_img, bool skip_comp = false);\n\nstruct boot_img {')
    hpp_path.write_text(content)
    print("✅ bootimg.hpp 已暴露 repack 声明！")

# 2. 全量重写 jni_wrapper.cpp
cpp_path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>

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
        // 获取当前工作目录并切到临时目录操作
        std::string initPathStr(init_p);
        std::string workDir = initPathStr.substr(0, initPathStr.find_last_of("/"));
        std::string oldCwd = getcwd(nullptr, 0);
        chdir(workDir.c_str());

        // 1. 加载原镜像
        boot_img boot(img_in);
        if (boot.hdr->ramdisk_size() == 0) {
            LOGE("Ramdisk is empty!");
            chdir(oldCwd.c_str());
            return JNI_FALSE;
        }

        // 2. 把原 ramdisk 写入 ramdisk.cpio (repack 默认读这个文件)
        FILE* fp = fopen("ramdisk.cpio", "wb");
        if (!fp) {
            LOGE("Failed to write ramdisk.cpio");
            chdir(oldCwd.c_str());
            return JNI_FALSE;
        }
        fwrite(boot.ramdisk, 1, boot.hdr->ramdisk_size(), fp);
        fclose(fp);

        // 3. 加载 cpio 并注入
        magisk_cpio cpio;
        cpio.load_cpio("ramdisk.cpio");
        cpio.patch_huijui(init_p, ko_p);
        
        // 4. 打包回 ramdisk.cpio
        cpio.dump("ramdisk.cpio");

        // 5. 调用底层原生的 repack 函数，它会自动处理对齐、头部修改和 AVB
        repack(img_in, img_out, false);

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
cpp_path.write_text(cpp_code)
print("✅ JNI 已重写完毕！现在它会自动切目录并调用原生 repack 完成 8MB 的构建！")
