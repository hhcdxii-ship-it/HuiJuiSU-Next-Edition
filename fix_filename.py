import os
import re
from pathlib import Path

print("🔍 正在全局搜索并精简镜像文件名 (保留时间戳)...")

# 匹配类似 HuiJuiPhantom_patched_init_boot_a_时间戳 或 HuiJuiPhantom_patched_${targetPartition}_时间戳 的拼接
# 核心逻辑是：把中间那段 "${targetPartition}_" 砍掉，前缀换成 huijui_patched

count = 0
for file in Path('.').glob('**/*.kt'):
    if 'build' in str(file): continue
    content = file.read_text()
    changed = False

    # 匹配 "HuiJuiPhantom_patched_init_boot_xxx_时间戳.img"
    if 'HuiJuiPhantom_patched_' in content:
        # 替换硬编码的 init_boot_a 或 boot_a
        new_content = re.sub(r'HuiJuiPhantom_patched_(init_boot|boot)[_a|_b]*_', 'huijui_patched_', content)
        # 替换动态的 ${targetPartition}_
        new_content = re.sub(r'HuiJuiPhantom_patched_\$\{targetPartition\}_', 'huijui_patched_', new_content)
        
        if new_content != content:
            content = new_content
            changed = True

    if changed:
        file.write_text(content)
        print(f"✅ 已精简 {file} 中的输出文件名")
        count += 1

if count == 0:
    print("⚠️ 没在代码里找到现成的拼接。请打开 UI 代码（如 CoreOverrideScreen.kt），手动找到这一行：")
    print('例如：val outPath = ".../HuiJuiPhantom_patched_${targetPartition}_${timeStamp}.img"')
    print('改成：val outPath = ".../huijui_patched_${timeStamp}.img"')
else:
    print("🎉 文件名精简大功告成！保留了时间戳，去掉了分区名！")
