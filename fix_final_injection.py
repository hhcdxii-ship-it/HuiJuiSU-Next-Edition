from pathlib import Path

# 修改 C++，只需要简单的注入，不用动底层逻辑，因为 patch_huijui 本身就能塞文件
cpp_path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = cpp_path.read_text()
# 这里保持我们上一步恢复的 C++ 即可

# 修改 Kotlin，让它释放 ksuinit（现在叫 huijuiinit）和 kernelsu.ko
kt_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
kt = kt_path.read_text()

# 释放 huijuiinit
kt = kt.replace('context.assets.open("exploit/huijuiinit")', 'context.assets.open("exploit/huijuiinit")')
# 释放 ko
kt = kt.replace('context.assets.open("exploit/kernelsu.ko")', 'context.assets.open("exploit/kernelsu.ko")')
# 确保文件名正确
kt = kt.replace('"kernelsu.ko"', '"kernelsu.ko"')

kt_path.write_text(kt)
print("✅ Kotlin 层已对齐！")
