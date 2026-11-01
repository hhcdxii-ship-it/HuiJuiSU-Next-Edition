from pathlib import Path
import shutil
import re
import os

print("🚀 正在执行纯血 HUI 终极魔改（把老版 KSU 源码变成咱们自己的）...")

old_kernel = Path('/home/xiaomi/HuijuiSU/kernel')
new_kernel = Path('/home/xiaomi/HuiJuiSU-Next-Edition/kernel')

# 1. 直接用老版强大的 KSU 源码树替换咱们玩具级的新版
if old_kernel.exists():
    if new_kernel.exists():
        shutil.rmtree(new_kernel)
    shutil.copytree(old_kernel, new_kernel)
    print("✅ 老版强大内核源码树已成功迁入新版！")
else:
    print("❌ 找不到老版源码，路劲错误！")
    exit(1)

# 2. 全局替换 KSU 标志为 HUI (打造纯血 Next 版)
# 遍历源码中的所有 C, H, Kconfig, Makefile 文件
count = 0
for root, dirs, files in os.walk(new_kernel):
    for file in files:
        if file.endswith(('.c', '.h', '.kconfig', '.mk', 'Makefile', 'Kconfig')):
            file_path = Path(root) / file
            try:
                content = file_path.read_text(errors='ignore')
                # 把 ksu 相关名字替换成 huijui
                new_content = content.replace('kernelsu', 'huijui').replace('KernelSU', 'HuiJuiSU').replace('ksu_', 'huijui_')
                if new_content != content:
                    file_path.write_text(new_content)
                    count += 1
            except Exception as e:
                pass
print(f"✅ 已把 {count} 个源码文件的命名空间从 KSU 改成了 HUI！")

# 3. 修改 Makefile 输出的模块名，改为咱们需要的名字
makefile_path = new_kernel / 'Makefile'
if makefile_path.exists():
    mk_content = makefile_path.read_text()
    # 强制改名为咱们要的 HuiJuiPhantom
    mk_content = mk_content.replace('huijui.ko', 'HuiJuiPhantom.ko')
    makefile_path.write_text(mk_content)
    print("✅ Makefile 编译输出已定为 HuiJuiPhantom.ko")
