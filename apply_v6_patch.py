import os
import re
from pathlib import Path

print("🚀 开始纯血 V6 一键注入...")

# ==========================================
# 1. 修改 build.gradle (防压缩)
# ==========================================
gradle_files = list(Path('.').glob('**/build.gradle*'))
gradle_found = False
for gf in gradle_files:
    if 'app' in str(gf) and gf.is_file():
        content = gf.read_text()
        if 'noCompress.add("huijuiinit")' not in content:
            if 'androidResources {' in content:
                content = content.replace('androidResources {', 'androidResources {\n        noCompress.add("huijuiinit")\n        noCompress.add("ko")')
            else:
                # 直接塞进 android {} 块里
                content = re.sub(r'android\s*\{', 'android {\n    androidResources {\n        noCompress.add("huijuiinit")\n        noCompress.add("ko")\n    }', content, count=1)
            gf.write_text(content)
            print(f"✅ 已修改 {gf}，添加防压缩规则")
            gradle_found = True
            break
if not gradle_found: print("⚠️ 没找到 app/build.gradle，请手动添加 noCompress")

# ==========================================
# 2. 修改 HsudInstaller.kt (释放 assets 并传参)
# ==========================================
kt_files = list(Path('.').glob('**/HsudInstaller.kt'))
if kt_files:
    kt_file = kt_files[0]
    content = kt_file.read_text()
    
    # 替换 JNI 签名
    content = re.sub(r'external fun nativePatch\([^)]*\)\s*:\s*Boolean', 
                     'external fun nativePatch(imagePath: String, targetPartition: String, initPath: String, koPath: String): Boolean', 
                     content)
    
    # 注入释放逻辑
    if 'val initFile = File(context.filesDir' not in content:
        inject_kotlin = '''
    fun releaseAssets(context: android.content.Context): Pair<String, String> {
        val targetDir = java.io.File(context.filesDir, "patch_env")
        if (!targetDir.exists()) targetDir.mkdirs()
        val initFile = java.io.File(targetDir, "huijuiinit")
        val koFile = java.io.File(targetDir, "huijui_phantom.ko")
        if (!initFile.exists()) {
            context.assets.open("exploit/huijuiinit").use { i -> initFile.outputStream().use { o -> i.copyTo(o) } }
            initFile.setExecutable(true)
        }
        if (!koFile.exists()) {
            context.assets.open("exploit/HuiJuiPhantom_android16-6.12_arm64.ko").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
        }
        return Pair(initFile.absolutePath, koFile.absolutePath)
    }
'''
        content = re.sub(r'class HsudInstaller', 'class HsudInstaller {\n' + inject_kotlin, content)
        kt_file.write_text(content)
        print(f"✅ 已修改 {kt_file}，注入 JNI 签名和 Assets 释放逻辑")
else:
    print("⚠️ 没找到 HsudInstaller.kt")

# ==========================================
# 3. 修改 jni_wrapper.cpp (接收路径并调用注入)
# ==========================================
cpp_files = list(Path('.').glob('**/jni_wrapper.cpp'))
if cpp_files:
    cpp_file = cpp_files[0]
    content = cpp_file.read_text()
    
    # 匹配并替换 JNI 函数签名
    pattern = r'JNIEXPORT jboolean JNICALL Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch\([^)]*\)\s*\{'
    replacement = '''JNIEXPORT jboolean JNICALL Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(
        JNIEnv* env, jobject, jstring imagePath, jstring targetPartition, jstring initPath, jstring koPath) {
    
    const char* img = env->GetStringUTFChars(imagePath, nullptr);
    const char* part = env->GetStringUTFChars(targetPartition, nullptr);
    const char* init_p = env->GetStringUTFChars(initPath, nullptr);
    const char* ko_p = env->GetStringUTFChars(koPath, nullptr);

    setenv("TARGET_PARTITION", part, 1);
    setenv("PATCHVBMETAFLAG", "true", 1);
'''
    content = re.sub(pattern, replacement, content)
    
    # 注入 patch_huijui 调用
    if 'cpio.patch_huijui' not in content:
        # 寻找解包后的合适位置插入，这里假设在 repack 之前，或者某些关键处理函数后
        # 兜底方案：直接找 return JNI_TRUE; 或者 return true; 并在其前面插
        inject_cpp = '''
    // 🔥 纯血 V6 核心注入调用
    // 注意：假设你上下文中有 magisk_cpio cpio; 这个对象
    if (cpio.find("init") != cpio.end()) {
        cpio.patch_huijui(init_p, ko_p);
    }
'''
        # 尝试在 return 之前注入
        content = content.replace('env->ReleaseStringUTFChars', inject_cpp + '\n    env->ReleaseStringUTFChars')
        
        # 顺手释放字符串
        if 'ReleaseStringUTFChars(initPath' not in content:
            content = content.replace('env->ReleaseStringUTFChars(targetPartition, part);', 
                                      'env->ReleaseStringUTFChars(targetPartition, part);\n    env->ReleaseStringUTFChars(initPath, init_p);\n    env->ReleaseStringUTFChars(koPath, ko_p);')

    cpp_file.write_text(content)
    print(f"✅ 已修改 {cpp_file}，完成底层 JNI 对接")
else:
    print("⚠️ 没找到 jni_wrapper.cpp")

print("🎉 纯血 V6 代码改造完毕！马上执行 ./gradlew assembleDebug 编译！")
