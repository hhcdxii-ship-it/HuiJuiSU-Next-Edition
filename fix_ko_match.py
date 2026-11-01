import re
from pathlib import Path

print("🚀 正在重写内核模块匹配逻辑，强制以内核版本优先...")

installer_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = installer_path.read_text()

# 正则匹配旧的 getDynamicKoName 函数
pattern = r'private fun getDynamicKoName\(\): String \{[\s\S]*?\n    \}'

new_func = '''private fun getDynamicKoName(): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        // 🔥 核心：优先从内核版本号里提取主次版本（比如 6.12.69 提取出 6.12）
        val regex = Regex("""(\\d+)\\.(\\d+)""")
        val match = regex.find(kernelVersion)
        val kmi = if (match != null) "\${match.groupValues[1]}.\${match.groupValues[2]}" else ""

        return when {
            kmi == "6.18" -> "HuiJuiPhantom_17_6_18_arm64.ko"
            kmi == "6.12" -> "HuiJuiPhantom_16_6_12_arm64.ko"
            kmi == "6.6"  -> "HuiJuiPhantom_15_6_6_arm64.ko"
            kmi == "6.1"  -> "HuiJuiPhantom_14_6_1_arm64.ko"
            kmi == "5.15" -> "HuiJuiPhantom_13_5_15_arm64.ko"
            kmi == "5.10" -> "HuiJuiPhantom_12_5_10_arm64.ko"
            
            // 内核版本极度奇葩时才退回用 SDK 版本兜底
            android.os.Build.VERSION.SDK_INT >= 37 -> "HuiJuiPhantom_17_6_18_arm64.ko"
            android.os.Build.VERSION.SDK_INT >= 36 -> "HuiJuiPhantom_16_6_12_arm64.ko"
            android.os.Build.VERSION.SDK_INT >= 35 -> "HuiJuiPhantom_15_6_6_arm64.ko"
            else -> "HuiJuiPhantom_16_6_12_arm64.ko"
        }
    }'''

content = re.sub(pattern, new_func, content)
installer_path.write_text(content)
print("✅ getDynamicKoName 已重写：现在严格根据 uname -r 匹配对应的 .ko 文件！")
print("🎉 修复完成！直接执行 ./gradlew assembleDebug 重新打包！")
