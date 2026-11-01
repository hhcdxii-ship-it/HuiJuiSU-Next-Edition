from pathlib import Path
import shutil
import glob

print("🚀 正在对齐底层资源文件名与 Kotlin 读取逻辑...")

asset_dir = Path('app/src/main/assets/exploit')
if not asset_dir.exists():
    asset_dir.mkdir(parents=True, exist_ok=True)

# 1. 强制把 huijuiinit 复制一份命名为 ksuinit，保证不管读哪个都有！
if (asset_dir / 'huijuiinit').exists():
    shutil.copy(asset_dir / 'huijuiinit', asset_dir / 'ksuinit')
    print("✅ 已将 huijuiinit 复制为 ksuinit！")
elif (asset_dir / 'ksuinit').exists():
    shutil.copy(asset_dir / 'ksuinit', asset_dir / 'huijuiinit')
    print("✅ 已将 ksuinit 复制为 huijuiinit！")
else:
    print("❌ 严重警告：exploit 目录里连个 init 文件都没有！")

# 2. 强制把咱们的 .ko 文件复制成 kernelsu.ko
ko_files = list(asset_dir.glob('HuiJuiPhantom_*.ko'))
if ko_files:
    # 挑选一个包含 6.12 的
    target_ko = next((f for f in ko_files if '6.12' in f.name), ko_files[0])
    shutil.copy(target_ko, asset_dir / 'kernelsu.ko')
    print(f"✅ 已复制 {target_ko.name} 为 kernelsu.ko！")
else:
    print("❌ 严重警告：找不到任何 HuiJuiPhantom.ko 文件！")

# 3. 顺手检查一下 Kotlin 代码，把里面所有的 ksuinit 都改回 huijuiinit（防呆）
kt_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
if kt_path.exists():
    content = kt_path.read_text()
    content = content.replace('exploit/ksuinit', 'exploit/huijuiinit')
    kt_path.write_text(content)
    print("✅ Kotlin 读取路径已强行修正为 exploit/huijuiinit！")
