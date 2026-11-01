import shutil
from pathlib import Path

print("🚀 正在给 App 注入终极 KSU 模板加载器...")

# 1. 把 KSU 的神仙 init 复制进 assets，改名为 huijuiinit
ksu_init_src = Path('/home/xiaomi/ksu_peek/out/init')
hui_init_dest = Path('app/src/main/assets/exploit/huijuiinit')
if ksu_init_src.exists():
    shutil.copy(ksu_init_src, hui_init_dest)
    print("✅ 已成功把 KSU 的神仙 init 偷过来，伪装成 huijuiinit！")
else:
    print("❌ 找不到 KSU 的 init 文件！")

# 2. 把咱们的 HUI 模块复制进 assets，改名为 kernelsu.ko
hui_ko_src = Path('app/src/main/assets/exploit/HuiJuiPhantom_android16-6.12_arm64.ko')
ko_dest = Path('app/src/main/assets/exploit/kernelsu.ko')
if hui_ko_src.exists():
    shutil.copy(hui_ko_src, ko_dest)
    print("✅ 已将 HUI 引擎伪装成 kernelsu.ko 放入 assets！")

# 3. 把 KSU 的配置文件也带上，防止它报错
ksu_cfg = Path('/home/xiaomi/ksu_peek/out/ksu_config')
if ksu_cfg.exists():
    shutil.copy(ksu_cfg, Path('app/src/main/assets/exploit/ksu_config'))
adb_cfg = Path('/home/xiaomi/ksu_peek/out/adb_debug.prop')
if adb_cfg.exists():
    shutil.copy(adb_cfg, Path('app/src/main/assets/exploit/adb_debug.prop'))
    
print("✅ 素材全部就位！")
