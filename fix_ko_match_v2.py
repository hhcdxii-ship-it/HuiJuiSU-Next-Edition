from pathlib import Path

print("🚀 正在修复 Python 转义问题并重写内核匹配逻辑...")

installer_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = installer_path.read_text()

# 1. 寻找旧函数的起止位置 (从 private fun getDynamicKoName 到下一个 private fun 或 类结尾)
start_idx = content.find('private fun getDynamicKoName(): String {')
if start_idx == -1:
    print("❌ 找不到 getDynamicKoName 函数！")
    exit(1)

# 找函数结束的标志（大括号匹配），这里用简单的逻辑，找下一个 "    }" 且后面跟着换行
end_idx = content.find('\n    }', start_idx)
if end_idx == -1:
    print("❌ 找不到函数结束位置！")
    exit(1)
end_idx += 6 # 包含 \n    }

# 2. 编写新的 Kotlin 函数（避开 Python 转义干扰）
# 使用 [0-9] 替代 \d，避免 Python 的 escape 警告
new_func = '''private fun getDynamicKoName(): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        // 🔥 核心：优先从内核版本号里提取主次版本（比如 6.12.69 提取出 6.12）
        val regex = Regex("""([0-9]+)\\.[0-9]+""")
        val match = regex.find(kernelVersion)
        
        var kmi = ""
        if (match != null) {
            val major = match.groupValues[1]
            val full = match.value
            kmi = full
        }

        return when {
            kmi.startsWith("6.18") -> "HuiJuiPhantom_17_6_18_arm64.ko"
            kmi.startsWith("6.12") -> "HuiJuiPhantom_16_6_12_arm64.ko"
            kmi.startsWith("6.6")  -> "HuiJuiPhantom_15_6_6_arm64.ko"
            kmi.startsWith("6.1")  -> "HuiJuiPhantom_14_6_1_arm64.ko"
            kmi.startsWith("5.15") -> "HuiJuiPhantom_13_5_15_arm64.ko"
            kmi.startsWith("5.10") -> "HuiJuiPhantom_12_5_10_arm64.ko"
            
            // 内核版本极度奇葩时才退回用 SDK 版本兜底
            android.os.Build.VERSION.SDK_INT >= 37 -> "HuiJuiPhantom_17_6_18_arm64.ko"
            android.os.Build.VERSION.SDK_INT >= 36 -> "HuiJuiPhantom_16_6_12_arm64.ko"
            android.os.Build.VERSION.SDK_INT >= 35 -> "HuiJuiPhantom_15_6_6_arm64.ko"
            else -> "HuiJuiPhantom_16_6_12_arm64.ko"
        }
    }'''

# 3. 暴力切片替换
new_content = content[:start_idx] + new_func + content[end_idx:]
installer_path.write_text(new_content)

print("✅ getDynamicKoName 已暴力重写！现在它绝对能精准匹配 6.12 内核了！")
print("🚀 直接执行 ./gradlew assembleDebug 起飞！")
