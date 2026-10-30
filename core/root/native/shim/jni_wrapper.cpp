#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>

extern "C" int hsud_main(int argc, char *argv[]);

extern "C" JNIEXPORT jint JNICALL
Java_com_huijui_sunext_edition_core_root_HsudNative_runCommand(
    JNIEnv *env, jobject thiz, jstring workDirStr, jobjectArray args) {
    if (!args) return -1;

    // 切换工作目录
    if (workDirStr) {
        const char *wd = env->GetStringUTFChars(workDirStr, nullptr);
        if (wd) {
            chdir(wd);
            env->ReleaseStringUTFChars(workDirStr, wd);
        }
    }

    int argc = env->GetArrayLength(args);
    std::vector<char*> argv;
    std::vector<std::string> string_pool;

    for (int i = 0; i < argc; ++i) {
        jstring js = (jstring) env->GetObjectArrayElement(args, i);
        const char *chars = env->GetStringUTFChars(js, nullptr);
        string_pool.push_back(std::string(chars));
        env->ReleaseStringUTFChars(js, chars);
        env->DeleteLocalRef(js);
    }

    for (int i = 0; i < argc; ++i) {
        argv.push_back(const_cast<char*>(string_pool[i].c_str()));
    }
    argv.push_back(nullptr);

    return hsud_main(argc, argv.data());
}
