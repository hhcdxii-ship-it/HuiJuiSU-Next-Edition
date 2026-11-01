import re
from pathlib import Path

print("🚀 正在重构输出路径，绕过 Android 沙盒限制...")

installer_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = installer_path.read_text()

# 1. 匹配 patchImage 函数
start_idx = content.find('fun patchImage(')
if start_idx == -1:
    print("❌ 找不到 patchImage！")
    exit(1)
end_idx = content.find('\n    }', start_idx) + 6

# 重写整个函数逻辑
new_func = '''fun patchImage(
        context: Context,
        imgPath: String,
        targetPartition: String,
        outputPath: String // 这里传进来的是最终的 /sdcard/Download/...
    ): Flow<PatchState> = flow {
        try {
            emit(PatchState.Progress("开始修补本地镜像..."))
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "huijui_phantom.ko").absolutePath
            
            // 动态匹配内核模块并释放
            val koName = getDynamicKoName(context)
            releaseAssets(context, koName)

            // 🚨 关键修改：先让 C++ 写到 App 私有缓存目录！
            val tempOutFile = File(context.cacheDir, "patched_sandbox.img")
            if (tempOutFile.exists()) tempOutFile.delete()

            emit(PatchState.Progress("正在调用 C++ 引擎执行注入..."))
            val success = nativePatch(imgPath, targetPartition, tempOutFile.absolutePath, initPath, koPath)
            
            if (success) {
                emit(PatchState.Progress("正在将镜像搬运至 Download 目录..."))
                // 由 Kotlin/Java 层负责拷贝，绕过 C++ 的权限问题
                val finalFile = File(outputPath)
                tempOutFile.copyTo(finalFile, overwrite = true)
                
                // 清理沙盒临时文件
                tempOutFile.delete()
                
                emit(PatchState.Success(outputPath))
            } else {
                emit(PatchState.Error("C++ 引擎执行失败！请查看 Logcat！"))
            }
        } catch (e: Throwable) {
            emit(PatchState.Error("底层致命异常拦截: ${e.message ?: "C++ 引擎崩溃"}"))
        }
    }.flowOn(Dispatchers.IO)'''

content = content[:start_idx] + new_func + content[end_idx:]
installer_path.write_text(content)
print("✅ HsudInstaller.kt 已重写：加入沙盒临时存储与安全搬运！")
