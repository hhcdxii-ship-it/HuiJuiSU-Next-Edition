import re
from pathlib import Path

print("🚀 正在执行终极物理切除：清理底层文件里的错误 zakoString 调用...")

target_files = [
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiFlashManager.kt',
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiModuleInstaller.kt'
]

for path in target_files:
    p = Path(path)
    if p.exists():
        content = p.read_text()
        
        # 1. 把所有的 zakoString("...") 强行拆掉，只保留纯文本
        # 匹配 zakoString("xxx") 变成 ("xxx") 或者纯字符串
        content = re.sub(r'zakoString\((.*?)\)', r'\1', content)
        
        # 2. 顺手把可能残留的 import 删掉
        content = re.sub(r'import com\.huijui\.sunext\.edition\.core\.ui\.language\.zakoString\n?', '', content)
        
        p.write_text(content)
        print(f"✅ {p.name} 的 zakoString 已彻底剥离！")
    else:
        print(f"❌ 找不到 {path}")

print("🎉 剥离完成！马上执行 ./gradlew assembleDebug")
