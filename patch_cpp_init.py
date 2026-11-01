import os
import re

# 尝试修改解包逻辑，如果你知道确切在哪个文件，可以改路径
file_path = 'core/root/native/boot/ramdisk.cpp' 

if not os.path.exists(file_path):
    print(f"❌ 找不到 {file_path}，请确认文件路径。")
    exit(1)

with open(file_path, 'r') as f:
    content = f.read()

# 寻找解包并处理文件的逻辑（这里假设是通过 entry 修改）
# 注入逻辑：将 init 重命名为 init.real
inject_code = '''
    // 🔥 纯血 KSU 劫持逻辑注入
    auto init_it = cpio.find("init");
    if (init_it != cpio.end()) {
        cpio["init.real"] = init_it->second;
        cpio.erase(init_it);
        fprintf(stderr, "[-] 已将原厂 init 重命名为 init.real\\n");
        
        // 注入 huijuiinit 作为新的 init
        std::string huijuiinit_data = read_file("/tmp/huijuiinit"); // 需要确保路径能读到
        cpio["init"] = huijuiinit_data;
        // 设置权限为 0750
        // ... (此处需根据你的 cpio 结构设置 mode)
        fprintf(stderr, "[-] 已注入 huijuiinit 伪装为 init\\n");
    } else {
        fprintf(stderr, "[-] 警告: ramdisk 中没有找到 init 文件\\n");
    }
'''

# 如果还没注入过，找一处末尾插入
if "init.real" not in content:
    # 在解包完成后插入，这里假设你有个函数叫 extract，在它结尾插入
    # 这是一个示例，具体注入点取决于你的代码结构
    print("✅ 准备注入逻辑。请手动确认 ramdisk.cpp 中的提取函数位置，将上述 inject_code 插入。")
    with open('inject_snippet.txt', 'w') as f_out:
        f_out.write(inject_code)
    print("✅ 注入代码片段已保存到 inject_snippet.txt，请手动贴入 ramdisk.cpp 的解包函数末尾。")
else:
    print("✅ 似乎已经注入过了。")
