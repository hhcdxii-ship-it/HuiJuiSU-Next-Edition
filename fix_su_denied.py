from pathlib import Path
import re

print("🚀 正在剥离本地修补对 su 的依赖...")

kt_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = kt_path.read_text()

# 找到 patchImage 方法并重写
start_idx = content.find('fun patchImage(')
if start_idx == -1:
    print("❌ 找不到 patchImage 方法！")
    exit(1)

end_idx = content.find('\n    }', start_idx) + 6

new_patch_method = '''fun patchImage(
        context: Context,
        imgPath: String,
        targetPartition: String,
        outputPath: String
    ): Flow<PatchState> = flow {
        try {
            emit(PatchState.Progress("开始修补本地镜像..."))
            val envDir = File(context.filesDir, "patch_env")
            val initPath = File(envDir, "huijuiinit").absolutePath
            val koPath = File(envDir, "kernelsu.ko").absolutePath

            // 强制释放资产文件（不需要 su）
            val koName = getDynamicKoName(context)
            releaseAssets(context, koName)

            // 🚨 核心修改：沙盒内处理，绝对不调用 su
            val tempOutFile = File(context.cacheDir, "patched_sandbox.img")
            if (tempOutFile.exists()) tempOutFile.delete()

            emit(PatchState.Progress("正在调用 C++ 引擎执行注入..."))
            val success = nativePatch(imgPath, targetPartition, tempOutFile.absolutePath, initPath, koPath)

            if (success) {
                emit(PatchState.Progress("正在将镜像搬运至 Download 目录..."))
                val finalFile = File(outputPath)
                tempOutFile.copyTo(finalFile, overwrite = true)
                tempOutFile.delete()
                emit(PatchState.Success(outputPath))
            } else {
                emit(PatchState.Error("C++ 引擎执行失败！请查看控制台日志"))
            }
        } catch (e: Throwable) {
            emit(PatchState.Error("修补异常: \${e.message}"))
        }
    }.flowOn(Dispatchers.IO)'''

content = content[:start_idx] + new_patch_method + content[end_idx:]

# 顺手把 releaseAssets 里可能残留的 su 也去掉
content = re.sub(r'Runtime\.getRuntime\(\)\.exec\(arrayOf\("su".*?\)\)', '// removed su', content)

kt_path.write_text(content)
print("✅ HsudInstaller.kt 已重写：本地修补彻底告别 su 依赖！")
