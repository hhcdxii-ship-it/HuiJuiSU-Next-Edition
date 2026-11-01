import re
from pathlib import Path

print("🚀 正在重构修补逻辑，规避系统底层监控碰撞...")

ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
content = ui_path.read_text()

# 把之前直接传入 tempFile 路径的逻辑，改成先把它放到 /data/local/tmp 下，避免小米安全沙盒的监控
old_logic = r'val targetPart = if \(sizeMB > 30\) "boot" else "init_boot"[\s\S]*?val outPath = "/sdcard/Download/huijui_patched_local_" \+ System\.currentTimeMillis\(\) \+ "\.img"'

new_logic = '''val targetPart = if (sizeMB > 30) "boot" else "init_boot"
                
                // 🔥 关键修改：把文件移到 /data/local/tmp 再操作，避开 MIUI 的沙盒监控
                val tmpDir = File("/data/local/tmp/huijui_safe")
                if (!tmpDir.exists()) tmpDir.mkdirs()
                val safeImg = File(tmpDir, "safe_input.img")
                try {
                    tempFile.copyTo(safeImg, overwrite = true)
                } catch (e: Exception) {
                    logText += "\\n> ❌ 无法复制到 /data/local/tmp，请检查 Root 权限！"
                    return@rememberLauncherForActivityResult
                }

                val outPath = "/sdcard/Download/huijui_patched_local_" + System.currentTimeMillis() + ".img"'''

content = re.sub(old_logic, new_logic, content, flags=re.DOTALL)

# 修改调用底层的参数，传入 safeImg 的路径
content = content.replace(
    'HsudInstaller.patchImage(context, tempFile.absolutePath, targetPart, outPath)',
    'HsudInstaller.patchImage(context, safeImg.absolutePath, targetPart, outPath)'
)

ui_path.write_text(content)
print("✅ UI 层已重构：先复制到 /data/local/tmp 再修补！")
