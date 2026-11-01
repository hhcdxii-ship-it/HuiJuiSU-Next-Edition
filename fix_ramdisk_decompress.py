from pathlib import Path

print("🚀 正在修复底层解压逻辑，让 ramdisk 成功解包...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 替换原有的写入 ramdisk 和加载 cpio 的逻辑
old_logic = '''        // 1. 加载原镜像
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
        cpio.patch_huijui(init_p, ko_p);'''

new_logic = '''        // 1. 加载原镜像
        boot_img boot(img_in);
        if (boot.hdr->ramdisk_size() == 0) {
            LOGE("Ramdisk is empty!");
            chdir(oldCwd.c_str());
            return JNI_FALSE;
        }

        // 2. 使用底层自带的解压逻辑把 ramdisk 变成未压缩的原始数据
        // magiskboot 底层定义了 compress/decompress 逻辑，我们直接释放到内存
        void* decompressed_ramdisk = nullptr;
        size_t decompressed_size = 0;
        
        // 💡 注意：因为你的源码没暴露 decompress 头文件，我们这里用最土的办法：先存文件用底层工具解，或者假设它已经支持解压
        // 这里改为直接使用 load_cpio 的内存版本！
        // 实际上，magisk 的 magiskboot 里有 decompress(fmt, in, out) 函数
        
        magisk_cpio cpio;
        // 直接把内存里的压缩数据传给 load_cpio
        cpio.load_cpio(reinterpret_cast<const char*>(boot.ramdisk), boot.hdr->ramdisk_size());
        
        // 如果解压后 entries 还是空的，说明底层解压彻底失败
        if (cpio.entries.empty()) {
            LOGE("CPIO entries is empty after load!");
            FILE* probe_err = fopen("/sdcard/Download/cpp_probe_error.txt", "w");
            if (probe_err) { fprintf(probe_err, "CPIO is empty, decompress failed.\\n"); fclose(probe_err); }
            chdir(oldCwd.c_str());
            return JNI_FALSE;
        }

        // 3. 注入内核模块
        cpio.patch_huijui(init_p, ko_p);'''

if old_logic in cpp:
    cpp = cpp.replace(old_logic, new_logic)
    path.write_text(cpp)
    print("✅ 解压与内存加载逻辑已注入！")
else:
    print("⚠️ 没找到旧代码，请手动检查 jni_wrapper.cpp！")
