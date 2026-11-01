import os
import re

WORK_DIR = os.path.expanduser("~/android-kernel")
# 找到刚刚拉下来的内核源码目录
if not os.path.exists(WORK_DIR):
    print("❌ 找不到内核源码目录，请先运行 build_all_local.sh")
    exit(1)

for d in os.listdir(WORK_DIR):
    dir_path = os.path.join(WORK_DIR, d)
    if not os.path.isdir(dir_path): continue

    print(f"🔧 正在给内核 {d} 打符号导出补丁...")
    
    # 1. 导出 security_add_hooks
    security_c = os.path.join(dir_path, "security", "security.c")
    if os.path.exists(security_c):
        content = open(security_c).read()
        if "EXPORT_SYMBOL(security_add_hooks)" not in content:
            content += "\nEXPORT_SYMBOL(security_add_hooks);\n"
            open(security_c, "w").write(content)
            print(f"  ✅ 已导出 security_add_hooks")

    # 2. 导出 security_secctx_to_secid
    secid_c = os.path.join(dir_path, "security", "security.c") # 有时在 security.c 里
    if os.path.exists(secid_c):
        content = open(secid_c).read()
        if "EXPORT_SYMBOL(security_secctx_to_secid)" not in content:
            content += "\nEXPORT_SYMBOL(security_secctx_to_secid);\n"
            open(secid_c, "w").write(content)
            print(f"  ✅ 已导出 security_secctx_to_secid")

print("🎉 所有内核源码的符号导出补丁已打完！")
