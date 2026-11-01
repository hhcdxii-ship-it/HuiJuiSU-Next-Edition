from pathlib import Path
import re

print("🚀 正在修复 JNI 库加载问题...")

installer_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = installer_path.read_text()

# 1. 在 object HsudInstaller 开头强行注入 System.loadLibrary
if 'System.loadLibrary("hsud")' not in content:
    content = content.replace(
        'object HsudInstaller {',
        'object HsudInstaller {\n    init {\n        try {\n            System.loadLibrary("hsud")\n        } catch (e: Throwable) {\n            e.printStackTrace()\n        }\n    }\n'
    )
    print("✅ 已强行注入 System.loadLibrary(\"hsud\")！")

# 2. 检查 nativePatch 的声明是否和 C++ 完全一致
# C++ 里是：Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(JNIEnv*, jobject, jstring, jstring, jstring, jstring, jstring)
# Kotlin 必须是：(String, String, String, String, String) -> Boolean
if 'external fun nativePatch' not in content:
    # 如果丢了就补在结尾
    content += '\n    external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean\n'
    print("✅ 已补全 nativePatch 的 external 声明！")
else:
    # 如果有，强制把参数修正
    content = re.sub(r'external fun nativePatch\([^)]*\)\s*:\s*Boolean', 
                     'external fun nativePatch(imagePath: String, targetPartition: String, outputPath: String, initPath: String, koPath: String): Boolean', 
                     content)

installer_path.write_text(content)
print("🎉 JNI 加载与接口修复完成！")
