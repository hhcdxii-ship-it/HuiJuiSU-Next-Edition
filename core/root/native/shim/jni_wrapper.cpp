#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>

#include <cstring>

#include "bootimg.hpp"
#include "magiskboot.hpp"
#include "ramdisk.hpp"
#include "compress.hpp"

extern void decompress(char *infile, const char *outfile);

#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) printf(__VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(
        JNIEnv* env, jobject, jstring imagePath, jstring targetPartition, jstring outputPath, jstring initPath, jstring koPath) {

    if (!imagePath || !outputPath || !initPath || !koPath) {
        LOGE("Null JNI strings received");
        return JNI_FALSE;
    }

    const char* img_in = env->GetStringUTFChars(imagePath, nullptr);
    const char* img_out = env->GetStringUTFChars(outputPath, nullptr);
    const char* init_p = env->GetStringUTFChars(initPath, nullptr);
    const char* ko_p = env->GetStringUTFChars(koPath, nullptr);

    if (!img_in || !img_out || !init_p || !ko_p) {
        LOGE("GetStringUTFChars failed");
        return JNI_FALSE;
    }

    LOGI("Native Patch Start: %s", img_in);

    try {
        // 1. 用最安全的 char 数组代替 std::string
        char workDirBuf[1024];
        strncpy(workDirBuf, init_p, sizeof(workDirBuf) - 1);
        workDirBuf[sizeof(workDirBuf) - 1] = '\0';
        char* last_slash = strrchr(workDirBuf, '/');
        if (last_slash) {
            *last_slash = '\0';
        } else {
            strcpy(workDirBuf, "/data/local/tmp"); // 兜底
        }

        char oldCwdBuf[1024];
        if (getcwd(oldCwdBuf, sizeof(oldCwdBuf)) == nullptr) {
            strcpy(oldCwdBuf, "/");
        }

        chdir(workDirBuf);

        boot_img boot(img_in);
        size_t r_sz = boot.hdr->ramdisk_size();
        uint8_t* r_data = boot.ramdisk;

        // 2. 文件路径也全用 char 数组
        char abs_comp[1024], abs_uncomp[1024];
        snprintf(abs_comp, sizeof(abs_comp), "%s/temp_r_compressed.bin", workDirBuf);
        snprintf(abs_uncomp, sizeof(abs_uncomp), "%s/temp_r_uncompressed.cpio", workDirBuf);

        FILE* fp_in = fopen(abs_comp, "wb");
        if (fp_in) {
            fwrite(r_data, 1, r_sz, fp_in);
            fclose(fp_in);
        }

        decompress(abs_comp, abs_uncomp);

        // 3. cpio 操作
        magisk_cpio cpio;
        cpio.load_cpio(abs_uncomp);
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump(abs_uncomp);

        // 4. 重命名
        char ramdisk_cpio[] = "ramdisk.cpio";
        rename(abs_uncomp, ramdisk_cpio);
        
        repack(img_in, img_out, false);

        // 5. 补齐 8MB
        struct stat st;
        if (stat(img_out, &st) == 0) {
            size_t original_size = boot.map.sz;
            if (st.st_size < original_size) {
                FILE* f_pad = fopen(img_out, "ab");
                if (f_pad) {
                    size_t to_pad = original_size - st.st_size;
                    char* zero_buf = (char*)calloc(1, to_pad);
                    if (zero_buf) {
                        fwrite(zero_buf, 1, to_pad, f_pad);
                        free(zero_buf);
                    }
                    fclose(f_pad);
                }
            }
        }

        unlink(ramdisk_cpio);
        unlink(abs_comp);
        unlink(abs_uncomp);
        chdir(oldCwdBuf);

        env->ReleaseStringUTFChars(imagePath, img_in);
        env->ReleaseStringUTFChars(outputPath, img_out);
        env->ReleaseStringUTFChars(initPath, init_p);
        env->ReleaseStringUTFChars(koPath, ko_p);

        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("C++ Exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown Exception during patching");
        return JNI_FALSE;
    }
}
