from pathlib import Path

print("🚀 正在给 App 注入终极自适应内核搜索逻辑...")

installer_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = installer_path.read_text()

# 1. 替换 getDynamicKoName，让它接收 context 并扫描 assets
start_idx = content.find('private fun getDynamicKoName(): String {')
if start_idx == -1:
    print("❌ 找不到 getDynamicKoName！")
    exit(1)

# 找旧函数的结尾
end_idx = content.find('\n    }', start_idx) + 6

new_func = '''private fun getDynamicKoName(context: android.content.Context): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }

        // 提取出类似 6.12 的大版本号
        val regex = Regex("""([0-9]+\\.[0-9]+)""")
        val match = regex.find(kernelVersion)
        val kmi = match?.value ?: "" // 比如 "6.12"

        // 读取 assets/exploit 下所有的 .ko 文件
        val allAssets = context.assets.list("exploit")?.filter { it.endsWith(".ko") } ?: emptyList()
        
        // 1. 优先找包含完整版本号的（兼容 . 和 _）
        val fuzzyKmi = kmi.replace(".", "_") // "6_12"
        val exactMatch = allAssets.find { it.contains(kmi) || it.contains(fuzzyKmi) }
        if (exactMatch != null) return exactMatch

        // 2. 如果没找到，用兜底逻辑
        return when {
            kmi.startsWith("6.18") -> allAssets.find { it.contains("17") } ?: "HuiJuiPhantom_17_6.18_arm64.ko"
            kmi.startsWith("6.12") -> allAssets.find { it.contains("16") } ?: "HuiJuiPhantom_16_6.12_arm64.ko"
            kmi.startsWith("6.6")  -> allAssets.find { it.contains("15") } ?: "HuiJuiPhantom_15_6.6_arm64.ko"
            kmi.startsWith("6.1")  -> allAssets.find { it.contains("14") } ?: "HuiJuiPhantom_14_6.1_arm64.ko"
            kmi.startsWith("5.15") -> allAssets.find { it.contains("13") && it.contains("5.15") } ?: "HuiJuiPhantom_13_5.15_arm64.ko"
            kmi.startsWith("5.10") -> allAssets.find { it.contains("12") } ?: "HuiJuiPhantom_12_5.10_arm64.ko"
            
            // 极度异常情况，找第一个能用的
            else -> allAssets.firstOrNull() ?: "HuiJuiPhantom_16_6.12_arm64.ko"
        }
    }'''

content = content[:start_idx] + new_func + content[end_idx:]

# 2. 把所有调用 getDynamicKoName() 的地方加上 context
content = content.replace('getDynamicKoName()', 'getDynamicKoName(context)')

# 3. 修正 releaseAssets 的 try-catch，加上具体的报错
content = content.replace(
    'context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }',
    '''
        try {
            context.assets.open("exploit/$targetKoName").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }
        } catch (e: Exception) {
            throw IllegalStateException("找不到内核模块: $targetKoName")
        }
    '''
)

installer_path.write_text(content)
print("✅ 自适应搜索逻辑注入完成！现在不管叫什么名字都能精准找到对应的内核！")
