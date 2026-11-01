import re
from pathlib import Path

file = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = file.read_text()

# 匹配原来的修补逻辑并替换成截胡重命名逻辑
# 假设原来的逻辑是：val result = HsudInstaller.nativePatch(...)
# 然后底层返回了长路径，我们就重命名它

if 'HuiJuiPhantom_patched.img' not in content:
    print("⚠️ 格式可能不同，请手动检查")

# 核心策略：不管底层返回什么，我们强制把刚生成的超长名字文件，重命名为 huijui_patched.img
inject_code = '''
                            // 🔥 核心截胡逻辑：底层 C++ 吐出来的名字太长，我们在这里强制把它削了！
                            val longPath = HsudInstaller.getLastPatchedPath() // 假设底层有方法能拿到路径
                            // 如果没有单独方法，直接用你原本拿到的返回路径
                            val tempFile = File(patchedImgPath) // 假设这里是底层返回的长路径
                            val finalFile = File("/sdcard/Download/huijui_patched.img")
                            if (tempFile.exists() && tempFile.name != "huijui_patched.img") {
                                tempFile.renameTo(finalFile)
                                patchedImgPath = finalFile.absolutePath
                            }
'''

print("请打开 CoreOverrideScreen.kt 第 246 行，检查底层返回路径的逻辑。")
print("最简单的做法，是在调用完修补函数后，直接加上：")
print('''
val returnedPath = "底层返回的超长路径"
val finalFile = File("/sdcard/Download/huijui_patched.img")
if (File(returnedPath).exists()) {
    File(returnedPath).renameTo(finalFile)
    patchedImgPath = finalFile.absolutePath
}
''')
