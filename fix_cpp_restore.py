from pathlib import Path

print("🚀 正在把底层 C++ 恢复成稳定的注入逻辑...")

path = Path('core/root/native/shim/jni_wrapper.cpp')

cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
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
        
        std::string temp_compressed = "temp_r_compressed.bin";
        FILE* fp_in = fopen(temp_compressed.c_str(), "wb");
        fwrite(r_data, 1, r_sz, fp_in);
        fclose(fp_in);

        std::string temp_uncompressed = "temp_r_uncompressed.cpio";
        char abs_comp[1024], abs_uncomp[1024];
        snprintf(abs_comp, sizeof(abs_comp), "%s/%s", workDir.c_str(), temp_compressed.c_str());
        snprintf(abs_uncomp, sizeof(abs_uncomp), "%s/%s", workDir.c_str(), temp_uncompressed.c_str());
        decompress(abs_comp, abs_uncomp);

        magisk_cpio cpio;
        cpio.load_cpio(temp_uncompressed.c_str());
        
        // 交给底层原本的 patch_huijui 去注入
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump(temp_uncompressed.c_str());

        rename(temp_uncompressed.c_str(), "ramdisk.cpio");

        repack(img_in, img_out, false);

        struct stat st;
        if (stat(img_out, &st) == 0) {
            size_t original_size = boot.map.sz;
            if (st.st_size < original_size) {
                FILE* f_pad = fopen(img_out, "ab");
                size_t to_pad = original_size - st.st_size;
                char* zero_buf = (char*)calloc(1, to_pad);
                fwrite(zero_buf, 1, to_pad, f_pad);
                fclose(f_pad);
                free(zero_buf);
            }
        }

        unlink("ramdisk.cpio");
        unlink(abs_comp);
        chdir(oldCwd.c_str());
        
        env->ReleaseStringUTFChars(imagePath, img_in);
        env->ReleaseStringUTFChars(outputPath, img_out);
        env->ReleaseStringUTFChars(initPath, init_p);
        env->ReleaseStringUTFChars(koPath, ko_p);
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during patching");
        return JNI_FALSE;
    }
}
'''
path.write_text(cpp_code)
print("✅ C++ 底层已恢复，利用原有的 patch_huijui 注入！")
