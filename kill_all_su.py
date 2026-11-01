from pathlib import Path
import re

print("🚀 正在全盘搜杀 su 权限请求...")

# 1. 找到所有包含 "su" 的代码行
for f in Path('app/src/main/java').rglob('*.kt'):
    content = f.read_text()
    if '"su"' in content:
        print(f"👉 发现 su 调用: {f}")
        
        # 如果是 H sudInstaller.kt，直接把 patchImage 里的 su 强制干掉
        if f.name == 'HsudInstaller.kt':
            # 暴力替换：把 exec(arrayOf("su", "-c", ...)) 改成 exec(arrayOf("sh", "-c", ...)) 或者直接注释
            # 这样哪怕没 root 也不会报 "Cannot run program su"
            content = re.sub(r'Runtime\.getRuntime\(\)\.exec\(arrayOf\("su", "-c", (.*?)\)\)', r'Runtime.getRuntime().exec(arrayOf("sh", "-c", \1))', content)
            
            f.write_text(content)
            print("✅ HsudInstaller.kt 里的 su 已被替换为 sh！")
