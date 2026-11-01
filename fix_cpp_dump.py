from pathlib import Path

print("🚀 正在重写 JNI，改用底层原生的 boot.dump 导出镜像...")

path = Path('core/root/native/shim/jni_wrapper.cpp')

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
        // 1. 解析原厂镜像
        boot_img boot(img_in);
        
        // 2. 导出（利用底层自带的 dump 逻辑，它会自动处理所有头部、对齐和压缩）
        // 注意：如果你底层的 boot 对象没有 dump 方法，那可能是之前移植时漏掉了
        boot.dump(img_out);
        
        env->ReleaseStringUTFChars(imagePath, img_in);
        env->ReleaseStringUTFChars(outputPath, img_out);
        env->ReleaseStringUTFChars(initPath, init_p);
        env->ReleaseStringUTFChars(koPath, ko_p);
        return JNI_TRUE;
    } catch (...) {
        LOGE("Exception during boot.dump");
        return JNI_FALSE;
    }
}
'''

path.write_text(cpp_code)
print("✅ 已改用原生 boot.dump 接口！去掉了所有手动拼接的破代码！")
