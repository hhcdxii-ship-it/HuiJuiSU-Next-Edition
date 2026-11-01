from pathlib import Path

print("🚀 正在重写 JNI 底层，加入日志监控探针...")

path = Path('core/root/native/shim/jni_wrapper.cpp')

cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "HuiJuiJNI", __VA_ARGS__)
#define HUIJUI_DEV "/dev/huijui"
#define HUIJUI_IOCTL_MAGIC 'H'
#define HUIJUI_IOCTL_GRANT_ROOT _IO(HUIJUI_IOCTL_MAGIC, 1)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(
        JNIEnv* env, jobject, jstring imagePath, jstring targetPartition, jstring outputPath, jstring initPath, jstring koPath) {
    
    const char* img_in = env->GetStringUTFChars(imagePath, nullptr);
    const char* img_out = env->GetStringUTFChars(outputPath, nullptr);
    const char* init_p = env->GetStringUTFChars(initPath, nullptr);
    const char* ko_p = env->GetStringUTFChars(koPath, nullptr);
    const char* part = env->GetStringUTFChars(targetPartition, nullptr);

    // 获取同目录下的 hsud 可执行文件路径
    std::string initPathStr(init_p);
    std::string dir = initPathStr.substr(0, initPathStr.find_last_of("/"));
    std::string hsudPath = dir + "/hsud";
    
    // 强制赋予执行权限
    chmod(hsudPath.c_str(), 0777);
    
    // 构造完整的 shell 命令，并将 stdout 和 stderr 写入到 Download 目录的日志文件
    std::string cmd = hsudPath + " patch " + img_in + " " + img_out + " " + part + " " + init_p + " " + ko_p;
    std::string full_cmd = "sh -c '" + cmd + " > /sdcard/Download/hsud_log.txt 2>&1'";
    
    LOGI("Executing: %s", full_cmd.c_str());

    // 使用 system 执行，它会自动阻塞并等待子进程结束
    int ret = system(full_cmd.c_str());

    env->ReleaseStringUTFChars(imagePath, img_in);
    env->ReleaseStringUTFChars(outputPath, img_out);
    env->ReleaseStringUTFChars(initPath, init_p);
    env->ReleaseStringUTFChars(koPath, ko_p);
    env->ReleaseStringUTFChars(targetPartition, part);

    LOGI("hsud execution result: %d", ret);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_huijui_sunext_edition_core_root_SuperUserManager_sendCommandToKernel(
        JNIEnv* env, jobject, jint command_code) {
    int fd = open(HUIJUI_DEV, O_RDWR);
    if (fd < 0) return JNI_FALSE;
    int ret = ioctl(fd, command_code);
    close(fd);
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}
'''

path.write_text(cpp_code)
print("✅ JNI 探针已注入：现在底层无论发生什么，都会把日志写进 hsud_log.txt！")
