#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <signal.h>
#include <cstdlib>
#include <android/log.h>

#define LOG_TAG "HsudJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" int hsud_main(int argc, char *argv[]);

extern "C" JNIEXPORT jint JNICALL
Java_com_huijui_sunext_edition_core_root_HsudNative_runCommand(
    JNIEnv *env, jobject thiz, jstring workDirStr, jobjectArray args) {
    if (!args) return -1;

    int argc = env->GetArrayLength(args);
    std::vector<char*> argv;
    std::vector<std::string> string_pool;

    for (int i = 0; i < argc; ++i) {
        jstring js = (jstring) env->GetObjectArrayElement(args, i);
        const char *chars = env->GetStringUTFChars(js, nullptr);
        string_pool.push_back(std::string(chars));
        env->ReleaseStringUTFChars(js, chars);
    }

    for (int i = 0; i < argc; ++i) {
        argv.push_back(const_cast<char*>(string_pool[i].c_str()));
    }
    argv.push_back(nullptr);

    const char *wd = nullptr;
    if (workDirStr) {
        wd = env->GetStringUTFChars(workDirStr, nullptr);
    }

    // 🚀 核心护盾：使用 fork 隔离
    pid_t pid = fork();

    if (pid == 0) {
        // --- 子进程区域 ---
        prctl(PR_SET_PDEATHSIG, SIGKILL); // 父进程死，子进程跟着死
        
        // 拦截导致闪退的异常信号，转成静默退出
        signal(SIGTRAP, SIG_DFL);
        signal(SIGSEGV, SIG_DFL);
        signal(SIGABRT, SIG_DFL);

        if (wd) {
            if (chdir(wd) != 0) _exit(-2);
        }

        int ret = hsud_main(argc, argv.data());
        _exit(ret); // 子进程强制退出，绝不波及主进程
    } else if (pid > 0) {
        // --- 父进程区域 ---
        int status;
        waitpid(pid, &status, 0);
        if (wd) env->ReleaseStringUTFChars(workDirStr, wd);

        if (WIFEXITED(status)) {
            return WEXITSTATUS(status); // 正常退出码
        } else if (WIFSIGNALED(status)) {
            LOGD("子进程崩溃，信号: %d", WTERMSIG(status));
            return -100 - WTERMSIG(status); // 返回异常退出码，方便 App 捕获
        }
        return -3;
    } else {
        if (wd) env->ReleaseStringUTFChars(workDirStr, wd);
        return -4; // fork 失败
    }
}
