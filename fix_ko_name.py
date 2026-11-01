from pathlib import Path

kt_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = kt_path.read_text()

# 强制把释放的模块名改为 kernelsu.ko
content = content.replace('val koFile = File(targetDir, "huijui_phantom.ko")', 'val koFile = File(targetDir, "kernelsu.ko")')

# 确保释放的是咱们新版编译出来的 6.12 ko
content = content.replace(
    'context.assets.open("exploit/HuiJuiPhantom_android16-6.12_arm64.ko").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }',
    'context.assets.open("exploit/kernelsu.ko").use { i -> koFile.outputStream().use { o -> i.copyTo(o) } }'
)

kt_path.write_text(content)
print("✅ Kotlin 逻辑修改完成：释放的 ko 已强制命名为 kernelsu.ko！")
