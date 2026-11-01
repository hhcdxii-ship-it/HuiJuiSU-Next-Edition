from pathlib import Path

print("🚀 正在恢复底层 repack 并强制补齐原厂大小...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 匹配整个 nativePatch 函数，用底层自带的 repack 重写
start_idx = cpp.find('extern "C" JNIEXPORT jboolean JNICALL')
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

        boot_img boot(img_in);
        size_t r_sz = boot.hdr->ramdisk_size();
        uint8_t* r_data = boot.ramdisk;
        
        // 1. 存压缩包 -> 解压 -> 注入
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
        cpio.patch_huijui(init_p, ko_p);
        cpio.dump(temp_uncompressed.c_str());

        // 2. 把注入后的 cpio 重命名给 repack 用
        rename(temp_uncompressed.c_str(), "ramdisk.cpio");

        // 3. 调用底层的 repack，让它自己搞定大小和头部！
        repack(img_in, img_out, false);

        // 4. 强制补齐到原厂大小 (8MB)
        struct stat st;
        if (stat(img_out, &st) == 0) {
            size_t original_size = boot.map.sz; // 原厂 8MB
            if (st.st_size < original_size) {
                FILE* f_pad = fopen(img_out, "ab");
                size_t to_pad = original_size - st.st_size;
                char* zero_buf = (char*)calloc(1, to_pad);
                fwrite(zero_buf, 1, to_pad, f_pad);
                fclose(f_pad);
                free(zero_buf);
                LOGI("Padded image to original size: %zu", original_size);
            }
        }

        // 清理
        unlink("ramdisk.cpio");
        unlink(abs_comp);
        chdir(oldCwd.c_str());
        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception during patching");
        return JNI_FALSE;
    }
}
'''

cpp = cpp[:start_idx] + new_code
path.write_text(cpp)
print("✅ 修补逻辑已恢复为原生 repack + 强制补齐 8MB！")
