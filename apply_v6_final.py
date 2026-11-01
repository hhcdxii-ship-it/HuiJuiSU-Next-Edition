import os
import re
from pathlib import Path

print("🚀 开始纯血 V6 终极一键注入 (带动态槽位与闭环逻辑)...")

# ==========================================
# 1. 修改 build.gradle (防压缩)
# ==========================================
gradle_files = list(Path('.').glob('**/build.gradle*'))
for gf in gradle_files:
    if 'app' in str(gf) and gf.is_file():
        content = gf.read_text()
        if 'noCompress.add("huijuiinit")' not in content:
            if 'androidResources {' in content:
                content = content.replace('androidResources {', 'androidResources {\n        noCompress.add("huijuiinit")\n        noCompress.add("ko")')
            else:
                content = re.sub(r'android\s*\{', 'android {\n    androidResources {\n        noCompress.add("huijuiinit")\n        noCompress.add("ko")\n    }', content, count=1)
            gf.write_text(content)
            print(f"✅ 已修改 {gf}，添加防压缩规则")
            break

# ==========================================
# 2. 全量覆写 HsudInstaller.kt (动态逻辑闭环)
# ==========================================
kt_code = """package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

object HsudInstaller {

    sealed class PatchState {
        data class Progress(val message: String) : PatchState()
        data class Success(val outputPath: String) : PatchState()
        data class Error(val errorMsg: String) : PatchState()
    }

    private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }

    // 🚨 动态获取当前活动的槽位 (A/B 分区)
    private fun getCurrentSlot(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix"))
            val slot = process.inputStream.bufferedReader().readText().trim()
            if (slot.isNotEmpty()) slot else "_a"
        } catch (e: Exception) { "_a" }
    }

    private fun releaseAssets(context: Context) {
        val targetDir = File(context.filesDir, "patch_env")
        if (!targetDir.exists()) targetDir.mkdirs()
        val initFile = File(targetDir, "huijuiinit")
        val koFile = File(targetDir, "huijui_phantom.ko")
        if (!initFile.exists() || initFile.length() < 1000) {
            context.assets.open("exploit/huijuiinit").use { i -> initFile.outputStream().use { o -> i.copyTo(o) } }
            initFile.setExecutable(true)
        }
        if (!koFile.exists() || koFile.length() < 1000) {
            context.assets.open("exploit/HuiJuiPhantom_android16-6.12_arm64.ko").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    /**
     * 🚀 终极绝杀：一键从底层提取 -> 动态修补 -> 刷入
     */
    fun directPatchAndFlash(targetPartition: String): Flow<PatchState> = flow {
        val context = appContext ?: throw IllegalStateException("未初始化 Context")
        try {
            val slot = getCurrentSlot()
            // 动态拼接分区名：比如 init_boot_a 或 boot_b
            val realPartitionName = "${targetPartition}${slot}" 
            val blockDevice = "/dev/block/by-name/$realPartitionName"
            
            emit(PatchState.Progress("[-] 动态锁定目标分区: $realPartitionName"))
            emit(PatchState.Progress("[-] 块设备路径: $blockDevice"))

            if (!File(blockDevice).exists()) {
                emit(PatchState.Error("致命错误：未找到分区节点 $blockDevice"))
                return@flow
            }

            val workDir = File("/data/local/tmp/huijui_patch")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()
            
            val origImg = File(workDir, "orig_$targetPartition.img")
            val patchedImg = File(workDir, "patched_$targetPartition.img")

            // 1. 提取原厂镜像
            emit(PatchState.Progress("[-] 正在从底层提取原厂镜像 (需要 Root)..."))
            val extractProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$blockDevice of=${origImg.absolutePath}"))
            if (extractProc.waitFor() != 0 || !origImg.exists()) {
                emit(PatchState.Error("提取失败！请检查 Root 权限。"))
                return@flow
            }
            
            // 动态验证提取出来的文件大小，防止刷错底层
            val sizeMB = origImg.length() / (1024 * 1024)
            emit(PatchState.Progress("[-] 镜像提取成功，实际大小: ${sizeMB}MB"))
            if (targetPartition == "init_boot" && sizeMB > 20) {
                emit(PatchState.Error("分区异常：提取出的 init_boot 大于 20MB，为防变砖已中止！"))
                return@flow
            }

            // 2. 释放依赖并注入
            releaseAssets(context)
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "huijui_phantom.ko").absolutePath

            emit(PatchState.Progress("[-] 正在调用底层 C++ 引擎执行注入..."))
            if (!nativePatch(origImg.absolutePath, targetPartition, patchedImg.absolutePath, initPath, koPath)) {
                emit(PatchState.Error("C++ 引擎执行失败！"))
                return@flow
            }

            // 3. 强制备份原厂
            val backupDir = File("/sdcard/HuiJuiSU_Backup")
            if (!backupDir.exists()) backupDir.mkdirs()
            val backupFile = File(backupDir, "backup_${realPartitionName}_${System.currentTimeMillis()}.img")
            emit(PatchState.Progress("[-] 备份原厂分区至: ${backupFile.name}"))
            origImg.copyTo(backupFile, overwrite = true)

            // 4. 刷入
            emit(PatchState.Progress("[-] 将纯血修补包动态写回 $realPartitionName..."))
            val flashProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=${patchedImg.absolutePath} of=$blockDevice bs=4096"))
            flashProc.errorStream.bufferedReader().forEachLine { 
                if (it.contains("bytes")) emit(PatchState.Progress("[-] 刷入中: $it")) 
            }

            if (flashProc.waitFor() == 0) {
                workDir.deleteRecursively()
                emit(PatchState.Success("🎉 修补并刷入 $realPartitionName 成功！请重启手机！"))
            } else {
                emit(PatchState.Error("写入失败！请使用备份包恢复。"))
            }
        } catch (e: Exception) {
            emit(PatchState.Error("执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean
}
"""
kt_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
kt_path.parent.mkdir(parents=True, exist_ok=True)
kt_path.write_text(kt_code)
print("✅ HsudInstaller.kt 全量重写！动态槽位 A/B、一键闭环修复完成！")

# ==========================================
# 3. 修改 jni_wrapper.cpp (注入动态传参)
# ==========================================
cpp_files = list(Path('.').glob('**/jni_wrapper.cpp'))
if cpp_files:
    cpp_file = cpp_files[0]
    content = cpp_file.read_text()
    
    # 替换 JNI 签名
    pattern = r'JNIEXPORT jboolean JNICALL Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch\([^)]*\)\s*\{'
    replacement = """JNIEXPORT jboolean JNICALL Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(
        JNIEnv* env, jobject, jstring imagePath, jstring targetPartition, jstring outputPath, jstring initPath, jstring koPath) {
    
    const char* img_in = env->GetStringUTFChars(imagePath, nullptr);
    const char* part = env->GetStringUTFChars(targetPartition, nullptr);
    const char* img_out = env->GetStringUTFChars(outputPath, nullptr);
    const char* init_p = env->GetStringUTFChars(initPath, nullptr);
    const char* ko_p = env->GetStringUTFChars(koPath, nullptr);

    // 核心魔法：动态传入分区名字，开启 VBMETA 破解
    setenv("TARGET_PARTITION", part, 1);
    setenv("PATCHVBMETAFLAG", "true", 1);
"""
    content = re.sub(pattern, replacement, content)
    
    # 注入底层调用
    if 'cpio.patch_huijui' not in content:
        inject_cpp = """
    // 🔥 纯血 V6 核心注入调用 (执行替换 init 和注入 ko)
    if (cpio.find("init") != cpio.end()) {
        cpio.patch_huijui(init_p, ko_p);
    }
"""
        # 插入到 return 之前
        content = content.replace('env->ReleaseStringUTFChars', inject_cpp + '\n    env->ReleaseStringUTFChars')

    # 补齐释放逻辑
    if 'ReleaseStringUTFChars(initPath' not in content:
        content = content.replace('env->ReleaseStringUTFChars(targetPartition, part);',
                                  'env->ReleaseStringUTFChars(targetPartition, part);\n    env->ReleaseStringUTFChars(imagePath, img_in);\n    env->ReleaseStringUTFChars(outputPath, img_out);\n    env->ReleaseStringUTFChars(initPath, init_p);\n    env->ReleaseStringUTFChars(koPath, ko_p);')
    
    cpp_file.write_text(content)
    print(f"✅ 已修改 {cpp_file}，打通 C++ 动态 JNI 接口")

print("🎉 纯血 V6 终极逻辑注入完成！马上执行编译：")
