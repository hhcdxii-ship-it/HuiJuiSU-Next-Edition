import os
import re
from pathlib import Path

print("🚀 开始一键注入：Android 12-17 全系 8 大内核模块编译与识别逻辑...")

# ==========================================
# 1. 全量重写 build_all_local.sh
# ==========================================
build_script = '''#!/bin/bash

# Android 12 - 17 全系 8 个 GKI 内核分支
BRANCHES=(
    "android12-5.10"
    "android13-5.10"
    "android13-5.15"
    "android14-5.15"
    "android14-6.1"
    "android15-6.6"
    "android16-6.12"
    "android17-6.18"
)

WORK_DIR=~/android-kernel
KDIR="$WORK_DIR/common"

if [ ! -d "$KDIR" ]; then
    echo "❌ 没找到 $KDIR，请确认内核源码路径！"
    exit 1
fi

cd $KDIR

for BRANCH in "${BRANCHES[@]}"; do
    echo "======================================================"
    echo "🚀 开始处理: $BRANCH"
    echo "======================================================"

    SHORT_NAME=$(echo $BRANCH | sed 's/android//;s/-/_/g')

    echo "🔄 1. 切换源码分支..."
    # 尝试切分支，失败则拉取并创建
    git checkout $BRANCH 2>/dev/null || (git fetch --depth 1 origin $BRANCH && git checkout -b $BRANCH FETCH_HEAD)
    git clean -fdx >/dev/null 2>&1
    git reset --hard HEAD >/dev/null 2>&1

    # 🔥 强力确保符号导出补丁存在
    if ! grep -q "EXPORT_SYMBOL(security_add_hooks)" security/security.c; then
        echo "⚠️ 符号补丁丢失，正在重新写入..."
        echo -e "\\nEXPORT_SYMBOL(security_add_hooks);\\nEXPORT_SYMBOL(security_secctx_to_secid);" >> security/security.c
    fi

    echo "⚙️ 2. 配置编译环境..."
    make -j$(nproc) ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- gki_defconfig
    make -j$(nproc) ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- modules_prepare

    echo "🔨 3. 编译 HuiJuiPhantom.ko..."
    cd ~/HuiJuiSU-Next-Edition/kernel
    make clean
    make -j$(nproc) ARCH=arm64 CC=clang LLVM=1 \\
      CROSS_COMPILE=aarch64-linux-gnu- \\
      KDIR=$KDIR \\
      KBUILD_MODPOST_WARN=1

    if [ -f "HuiJuiPhantom.ko" ]; then
        echo "✅ 编译成功，正在瘦身并改名..."
        llvm-strip --strip-debug HuiJuiPhantom.ko
        cp HuiJuiPhantom.ko ../app/src/main/assets/exploit/HuiJuiPhantom_${SHORT_NAME}_arm64.ko
        echo "📦 已保存至 assets: HuiJuiPhantom_${SHORT_NAME}_arm64.ko"
    else
        echo "❌ 编译失败！"
    fi

    echo "🧹 4. 准备下一个版本..."
    cd $KDIR
done

echo "🎉 8 大内核模块编译完毕！去 app/src/main/assets/exploit/ 检阅吧！"
'''
Path('build_all_local.sh').write_text(build_script)
os.system('chmod +x build_all_local.sh')
print("✅ build_all_local.sh 已重写：加入 8 个 GKI 分支！")

# ==========================================
# 2. 更新 Kotlin 动态识别逻辑
# ==========================================
kt_path = 'app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt'
kt_file = Path(kt_path)

if kt_file.exists():
    content = kt_file.read_text()
    
    # 正则匹配旧的 getDynamicKoName 函数
    pattern = r'private fun getDynamicKoName\(context: Context\): String \{[\s\S]*?\n    \}'
    
    new_func = '''private fun getDynamicKoName(context: Context): String {
        val kernelVersion = try {
            Runtime.getRuntime().exec(arrayOf("uname", "-r")).inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "" }
        
        val sdkInt = android.os.Build.VERSION.SDK_INT

        // 优先根据系统 SDK 版本和内核版本进行双重匹配
        return when {
            // Android 17 (SDK 37) / 6.18
            sdkInt >= 37 || kernelVersion.contains("6.18") -> "HuiJuiPhantom_17_6_18_arm64.ko"
            
            // Android 16 (SDK 36) / 6.12
            sdkInt >= 36 || kernelVersion.contains("6.12") -> "HuiJuiPhantom_16_6_12_arm64.ko"
            
            // Android 15 (SDK 35) / 6.6
            sdkInt >= 35 || kernelVersion.contains("6.6")  -> "HuiJuiPhantom_15_6_6_arm64.ko"
            
            // Android 14 (SDK 34)
            sdkInt == 34 -> {
                if (kernelVersion.contains("5.15")) "HuiJuiPhantom_14_5_15_arm64.ko"
                else "HuiJuiPhantom_14_6_1_arm64.ko"
            }
            
            // Android 13 (SDK 33)
            sdkInt == 33 -> {
                if (kernelVersion.contains("5.15")) "HuiJuiPhantom_13_5_15_arm64.ko"
                else "HuiJuiPhantom_13_5_10_arm64.ko"
            }
            
            // Android 12 (SDK 31/32)
            sdkInt <= 32 || kernelVersion.contains("5.10") -> "HuiJuiPhantom_12_5_10_arm64.ko"
            
            // 兜底逻辑
            else -> {
                if (kernelVersion.contains("6.1")) "HuiJuiPhantom_14_6_1_arm64.ko"
                else if (kernelVersion.contains("5.15")) "HuiJuiPhantom_13_5_15_arm64.ko"
                else "HuiJuiPhantom_16_6_12_arm64.ko"
            }
        }
    }'''

    if re.search(pattern, content):
        content = re.sub(pattern, new_func, content)
        kt_file.write_text(content)
        print("✅ HsudInstaller.kt 更新完毕：动态匹配 8 大内核模块！")
    else:
        print("❌ 没找到 getDynamicKoName 函数，请手动替换。")
else:
    print("❌ 没找到 HsudInstaller.kt 文件！")

print("🎉 全部就绪！现在可以执行 ./build_all_local.sh 了！")
