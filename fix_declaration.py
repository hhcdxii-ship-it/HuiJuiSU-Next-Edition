import re
from pathlib import Path

# 如果上面 grep 搜出来的路径不是这个，请把下面的路径替换掉
hpp_file = Path('core/root/native/boot/cpio.hpp')

if hpp_file.exists():
    content = hpp_file.read_text()
    
    if 'patch_huijui' not in content:
        # 匹配 class magisk_cpio { ... 后面的第一个 public:
        pattern = r'(class\s+magisk_cpio\s*\{[\s\S]*?public:)'
        # 在 public: 后面直接插入声明
        replacement = r'\1\n    void patch_huijui(const char* huijuiinit_path, const char* ko_path);'
        
        new_content = re.sub(pattern, replacement, content, count=1)
        
        if new_content != content:
            hpp_file.write_text(new_content)
            print("✅ 成功！已在 cpio.hpp 的 magisk_cpio 类中补上 patch_huijui 声明！")
        else:
            print("❌ 正则没匹配到，可能是你的 class 结构比较特殊。请手动打开 cpio.hpp，在 class magisk_cpio 的 public: 下面加一行：\n    void patch_huijui(const char* huijuiinit_path, const char* ko_path);")
    else:
        print("✅ 声明已经存在，不需要修改。")
else:
    print("❌ 找不到 cpio.hpp，请确认路径！")
