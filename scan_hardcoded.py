import os
import re
from pathlib import Path

print("🔍 开始地毯式扫描未被 zako 包装的硬编码中文...\n")

base_dir = Path('app/src/main/java')
issues_found = 0

for kt_file in base_dir.rglob('*.kt'):
    if kt_file.name == 'ZakoText.kt':
        continue

    lines = kt_file.read_text().split('\n')
    file_reported = False

    for i, line in enumerate(lines, 1):
        # 跳过单行注释和多行注释
        stripped_line = line.strip()
        if stripped_line.startswith('//') or stripped_line.startswith('*') or stripped_line.startswith('/*'):
            continue

        # 如果这一行已经包含了 zakoString 或 ZakoText，说明已经处理过了，跳过
        if 'zakoString(' in line or 'ZakoText(' in line:
            continue

        # 匹配双引号内的中文（排除带有 Log.d/Log.e, 因为底层调试日志我们需要它保持原样方便抓 bug）
        # 如果是 Log.d("TAG", "中文")，我们通常不翻译
        if 'Log.' in line:
            continue

        matches = re.findall(r'"([^"]*[\u4e00-\u9fa5]+[^"]*)"', line)
        
        if matches:
            if not file_reported:
                print(f"📂 文件: {kt_file.relative_to(base_dir)}")
                file_reported = True
            
            issues_found += len(matches)
            # 截取上下文，方便定位
            print(f"  行 {i:4d} | {stripped_line[:100]}")
            print(f"         👉 发现硬编码: {matches}")
            print("-" * 60)

print(f"\n🎯 扫描完毕！共发现 {issues_found} 处可能漏网的硬编码中文字符串。")
print("⚠️ 注意：请人工审视以上列表，确认哪些是真正需要被加入 ZakoText 字典的 UI 文本。")
