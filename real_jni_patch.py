from pathlib import Path

print("🚀 正在给 JNI 注入真正的底层干活逻辑...")

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
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HuiJuiJNI", __VA_ARGS__)
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

    LOGI("Native Patch Start: %s", img_in);

    // 获取同目录下的 hsud 可执行文件路径
    std::string initPathStr(init_p);
    std::string dir = initPathStr.substr(0, initPathStr.find_last_of("/"));
    std::string hsudPath = dir + "/hsud";
    
    chmod(hsudPath.c_str(), 0755);
    
    pid_t pid = fork();
    if (pid == 0) {
        // 子进程隔离：静默输出，防止死锁
        int dev_null = open("/dev/null", O_WRONLY);
        if (dev_null != -1) {
            dup2(dev_null, STDOUT_FILENO);
            dup2(dev_null, STDERR_FILENO);
        }
        
        // 构建参数: hsud patch <in> <out> <part> <init> <ko>
        std::string a1 = hsudPath;
        std::string a2 = "patch";
        std::string a3 = img_in;
        std::string a4 = img_out;
        std::string a5 = part;
        std::string a6 = init_p;
        std::string a7 = ko_p;
        
        char* const argv[] = {
            const_cast<char*>(a1.c_str()),
            const_cast<char*>(a2.c_str()),
            const_cast<char*>(a3.c_str()),
            const_cast<char*>(a4.c_str()),
            const_cast<char*>(a5.c_str()),
            const_cast<char*>(a6.c_str()),
            const_cast<char*>(a7.c_str()),
            nullptr
        };
        
        execv(hsudPath.c_str(), argv);
        exit(1); // 如果 execv 失败
    }
    
    int status = 0;
    waitpid(pid, &status, 0);
    
    env->ReleaseStringUTFChars(imagePath, img_in);
    env->ReleaseStringUTFChars(outputPath, img_out);
    env->ReleaseStringUTFChars(initPath, init_p);
    env->ReleaseStringUTFChars(koPath, ko_p);
    env->ReleaseStringUTFChars(targetPartition, part);
    
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
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
print("✅ JNI 已重写：现在它不再是假动作，而是会真实拉起 hsud 引擎干活！")
