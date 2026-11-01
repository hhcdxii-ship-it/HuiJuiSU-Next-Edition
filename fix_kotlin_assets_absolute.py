from pathlib import Path

print("🚀 正在修改 Kotlin 释放逻辑，直接喂给底层...")

path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = path.read_text()

# 把要找的 huijuiinit 换成 ksuinit
content = content.replace('"huijuiinit"', '"ksuinit"')
content = content.replace('"huijui_phantom.ko"', '"kernelsu.ko"')
content = content.replace('exploit/huijuiinit', 'exploit/ksuinit')
content = content.replace('exploit/HuiJuiPhantom_android16-6.12_arm64.ko', 'exploit/kernelsu.ko')

path.write_text(content)
print("✅ Kotlin 层已修改：现在释放的是 ksuinit 和 kernelsu.ko！")
