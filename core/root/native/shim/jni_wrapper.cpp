#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <signal.h>
#include <cstdlib>
#include <cstdio>
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

    pid_t pid = fork();

    if (pid == 0) {
        // --- 子进程区域 ---
        prctl(PR_SET_PDEATHSIG, SIGKILL);
        
        // 🚀 将标准输出和错误输出重定向到黑洞，防止底层疯狂打日志导致管道阻塞死锁
        freopen("/dev/null", "w", stdout);
        freopen("/dev/null", "w", stderr);

        if (wd) {
            if (chdir(wd) != 0) _exit(-2);
        }

        int ret = hsud_main(argc, argv.data());
        _exit(ret);
    } else if (pid > 0) {
        // --- 父进程区域 ---
        int status = 0;
        int timeout_ms = 15000; 
        int waited_ms = 0;

        while (waited_ms < timeout_ms) {
            pid_t res = waitpid(pid, &status, WNOHANG);
            if (res == pid) break; 
            usleep(100000); 
            waited_ms += 100;
        }

        if (waited_ms >= timeout_ms) {
            LOGD("子进程执行超时，强杀！");
            kill(pid, SIGKILL);
            waitpid(pid, &status, 0); 
            if (wd) env->ReleaseStringUTFChars(workDirStr, wd);
            return -911; // 超时强杀返回码
        }

        if (wd) env->ReleaseStringUTFChars(workDirStr, wd);

        if (WIFEXITED(status)) return WEXITSTATUS(status);
        else if (WIFSIGNALED(status)) return -100 - WTERMSIG(status);
        return -3;
    } else {
        if (wd) env->ReleaseStringUTFChars(workDirStr, wd);
        return -4;
    }
}
