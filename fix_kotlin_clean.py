from pathlib import Path
import re

kt_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
content = kt_path.read_text()

# 把 kernelsu.ko 换回咱们自己的 huijui_phantom.ko
content = content.replace('kernelsu.ko', 'huijui_phantom.ko')
# 把所有释放逻辑改回咱们自己的 huijuiinit
content = content.replace('"huijuiinit"', '"huijuiinit"') 
content = content.replace('exploit/ksuinit', 'exploit/huijuiinit')

# 彻底剥除本地修补里可能残留的 su 调用
content = re.sub(r'Runtime\.getRuntime\(\)\.exec\(arrayOf\("su", "-c", (.*?)\)\)', r'Runtime.getRuntime().exec(arrayOf("sh", "-c", \1))', content)

kt_path.write_text(content)
print("✅ HsudInstaller.kt 已还原为纯血逻辑！")
