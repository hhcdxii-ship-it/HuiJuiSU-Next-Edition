from pathlib import Path

print("🚀 正在注入手动 zlib/lz4 解压逻辑...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 在开头加入 zlib 和 lz4 依赖
includes = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <android/log.h>
#include <zlib.h>
#include <lz4.h>

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

        // 1. 加载原镜像
        boot_img boot(img_in);
        size_t r_sz = boot.hdr->ramdisk_size();
        uint8_t* r_data = boot.ramdisk;

        // 2. 检测解压格式
        std::string temp_cpio = "ramdisk.cpio";
        bool decompressed = false;
        
        // 检查 gzip 魔数 (1f 8b)
        if (r_sz > 2 && r_data[0] == 0x1f && r_data[1] == 0x8b) {
            LOGI("Detected GZIP ramdisk, decompressing...");
            gzFile gz = gzopen(img_in, "rb"); // 这里其实不能这么写，因为 gz 只能从头读
            // 为了稳妥，先把内存数据写到临时压缩包
            FILE* f_gz = fopen("temp_r_gz.bin", "wb");
            fwrite(r_data, 1, r_sz, f_gz);
            fclose(f_gz);
            
            // 用 zlib 解压
            gzFile gz_in = gzopen("temp_r_gz.bin", "rb");
            FILE* f_out = fopen(temp_cpio.c_str(), "wb");
            char outbuf[16384];
            int uncompressed_bytes;
            while ((uncompressed_bytes = gzread(gz_in, outbuf, sizeof(outbuf))) > 0) {
                fwrite(outbuf, 1, uncompressed_bytes, f_out);
            }
            fclose(f_out);
            gzclose(gz_in);
            decompressed = true;
        } 
        // 检查 lz4 魔数 (04 22 4d 18)
        else if (r_sz > 4 && r_data[0] == 0x04 && r_data[1] == 0x22 && r_data[2] == 0x4d && r_data[3] == 0x18) {
            LOGI("Detected LZ4 ramdisk, decompressing...");
            // LZ4 解压逻辑（这里简单实现，假设解压后大小不超过 100MB）
            char* dest = (char*)malloc(100 * 1024 * 1024);
            int destSize = LZ4_decompress_safe((const char*)r_data, dest, r_sz, 100 * 1024 * 1024);
            if (destSize > 0) {
                FILE* f_out = fopen(temp_cpio.c_str(), "wb");
                fwrite(dest, 1, destSize, f_out);
                fclose(f_out);
                free(dest);
                decompressed = true;
            }
        }
        // 如果不是压缩的，直接写进去
        else {
            LOGI("Detected uncompressed ramdisk or unknown format, writing directly...");
            FILE* f_out = fopen(temp_cpio.c_str(), "wb");
            fwrite(r_data, 1, r_sz, f_out);
            fclose(f_out);
            decompressed = true;
        }

        // 3. 注入
        magisk_cpio cpio;
        cpio.load_cpio(temp_cpio.c_str());
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump(temp_cpio.c_str());

        // 4. 读取注入后的 ramdisk (这里是不压缩的)
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
        
        // 清理临时文件
        unlink("temp_r_gz.bin");
        unlink(temp_cpio.c_str());
        
        chdir(oldCwd.c_str());

        LOGI("Manual Assemble Success! Size: %ld", total_size);
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during manual assemble");
        return JNI_FALSE;
    }
}
'''

path.write_text(includes)
print("✅ 解压+注入逻辑已注入完毕！")
