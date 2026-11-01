import re
from pathlib import Path

file_path = Path('core/root/native/boot/ramdisk.cpp')
if not file_path.exists():
    print("❌ 找不到 ramdisk.cpp")
    exit(1)

content = file_path.read_text()

if 'void patch_huijui' not in content:
    # 匹配 class magisk_cpio : public cpio { 后面的 public: 区域
    pattern = r'(class\s+magisk_cpio\s*:\s*public\s+cpio\s*\{[\s\S]*?public:)'
    replacement = r'\1\n    void patch_huijui(const char* huijuiinit_path, const char* ko_path);'
    
    new_content = re.sub(pattern, replacement, content, count=1)
    
    if new_content != content:
        file_path.write_text(new_content)
        print("✅ 成功！已在 ramdisk.cpp 的 magisk_cpio 类中添加 patch_huijui 声明！")
    else:
        print("❌ 还是没匹配到。请手动打开 ramdisk.cpp 第 17 行，在 public: 后面加上：\n    void patch_huijui(const char* huijuiinit_path, const char* ko_path);")
else:
    print("✅ 声明已经存在。")
