from pathlib import Path

path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = path.read_text()

# 换回咱们自己的 huijuiinit
content = content.replace('"ksuinit"', '"huijuiinit"')
content = content.replace('"kernelsu.ko"', '"huijui_phantom.ko"')
content = content.replace('exploit/ksuinit', 'exploit/huijuiinit')
content = content.replace('exploit/kernelsu.ko', 'exploit/HuiJuiPhantom_android16-6.12_arm64.ko')

path.write_text(content)
print("✅ Kotlin 已换回纯血 HUI 逻辑！")
