import re
from pathlib import Path

print("🚀 正在精准爆破解码最后的报错...")

# 1. 强制修复 MainActivity.kt
main_kt = Path('app/src/main/java/com/huijui/sunext/edition/MainActivity.kt')
if main_kt.exists():
    content = main_kt.read_text()
    # 直接删掉传进去的 onBack 参数
    content = content.replace('CoreOverrideScreen(onBack = { navController.popBackStack() })', 'CoreOverrideScreen()')
    # 兜底：把可能有变体的也删了
    content = re.sub(r'CoreOverrideScreen\([^)]*onBack[^)]*\)', 'CoreOverrideScreen()', content)
    main_kt.write_text(content)
    print("✅ MainActivity.kt 的 onBack 参数已物理清除！")

# 2. 暴力补全底层文件的 import
target_files = [
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiFlashManager.kt',
    'app/src/main/java/com/huijui/sunext/edition/core/root/HuijuiModuleInstaller.kt'
]

for f_path in target_files:
    f = Path(f_path)
    if f.exists():
        content = f.read_text()
        # 强制把 import 写死在 package 下面
        if 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in content:
            # 找到 package 行，在它后面强插
            content = re.sub(
                r'(package\s+com\.huijui\.sunext\.edition\.core\.root)',
                r'\1\n\nimport com.huijui.sunext.edition.core.ui.language.zakoString',
                content, count=1
            )
            f.write_text(content)
            print(f"✅ {f.name} 强行注入了 zakoString 的 Import！")
        else:
            print(f"⚠️ {f.name} 已经有了 import，可能是作用域问题。")

print("🎉 终极抢救完成！直接执行 ./gradlew assembleDebug！")
