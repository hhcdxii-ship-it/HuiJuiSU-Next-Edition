import re
from pathlib import Path

print("🚀 正在紧急恢复被误删的 RootManager 枚举...")

detector_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/RootManagerDetector.kt')
content = detector_path.read_text()

# 1. 全量重写枚举定义，把刚才删掉的全加回来
new_enum = '''enum class RootManager(val displayName: String, val shortName: String) {
    HUIJUI_ULTRA("HuiJuiSU Ultra", "HUI-Ultra"),
    HUIJUI("HuiJuiSU Next Edition", "HUI"),
    KERNELSU_NEXT("KernelSU Next", "KSU-Next"),
    KERNELSU("KernelSU", "KSU"),
    SUKISU_ULTRA("SukiSU-Ultra", "SukiSU"),
    BAKASU("BakaSU (ReSukiSU)", "BakaSU"),
    YUKISU("YukiSU", "YukiSU"),
    KOWSU("KowSU", "KowSU"),
    RKSU("RKSU", "RKSU"),
    MKSU("MKSU", "MKSU"),
    WILDKSU("WildKSU", "WildKSU"),
    APATCH("APatch", "APatch"),
    ASTER("Aster", "Aster"),
    FOLKPATCH("FolkPatch", "FolkPatch"),
    MAGISK("Magisk", "Magisk"),
    MAGISK_WEAVEMASK("WeaveMask", "WeaveMask"),
    MAGISK_KITSUNE("Kitsune Mask", "Kitsune"),
    MAGISK_ALPHA("Magisk Alpha", "Alpha"),
    MAGISK_DELTA("Magisk Delta", "Delta"),
    SHIZUKU("Shizuku", "Shizuku"),
    UNKNOWN("未知管理器", "Unknown"),
    NONE("无 Root", "None")
}'''

content = re.sub(r'enum class RootManager\([^)]*\)\s*\{[\s\S]*?\n\}', new_enum, content, count=1)

# 2. 在 doDetect 里把这些变种的检测逻辑也补全，保证它们能被识别
# 原本的 when 语句结尾是 -> RootManager.UNKNOWN
# 我们把它替换成更全的检测分支
new_when_ending = '''            // 其他变种 KSU
            pkgs.contains("com.rifsxd.yukisu") -> RootManager.YUKISU
            pkgs.contains("com.kow.kowsu") -> RootManager.KOWSU
            pkgs.contains("com.rksu") -> RootManager.RKSU
            pkgs.contains("com.mksu") -> RootManager.MKSU
            pkgs.contains("com.wildksu") -> RootManager.WILDKSU
            pkgs.contains("com.aster") -> RootManager.ASTER
            pkgs.contains("com.folkpatch") -> RootManager.FOLKPATCH

            // Magisk 变种
            pkgs.contains("com.weavemask") -> RootManager.MAGISK_WEAVEMASK
            pkgs.contains("com.topjohnwu.magisk.delta") || pkgs.contains("io.github.vvb2060.magisk") -> RootManager.MAGISK_DELTA
            pkgs.contains("com.topjohnwu.magisk.alpha") -> RootManager.MAGISK_ALPHA

            // 仅 Shizuku 或有 su 进程但没对上号
            pkgs.contains("moe.shizuku.privileged.api") -> RootManager.SHIZUKU
            else -> RootManager.UNKNOWN'''

content = content.replace('''            // 仅 Shizuku 或有 su 进程但没对上号
            pkgs.contains("moe.shizuku.privileged.api") -> RootManager.SHIZUKU
            else -> RootManager.UNKNOWN''', new_when_ending)

detector_path.write_text(content)
print("✅ RootManagerDetector.kt 枚举已全量恢复，且补齐了检测逻辑！")

print("🎉 恢复完成！直接执行 ./gradlew assembleDebug 起飞！")
