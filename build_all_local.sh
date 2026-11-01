#!/bin/bash

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
        echo -e "\nEXPORT_SYMBOL(security_add_hooks);\nEXPORT_SYMBOL(security_secctx_to_secid);" >> security/security.c
    fi

    echo "⚙️ 2. 配置编译环境..."
    make -j$(nproc) ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- gki_defconfig
    make -j$(nproc) ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- modules_prepare

    echo "🔨 3. 编译 HuiJuiPhantom.ko..."
    cd ~/HuiJuiSU-Next-Edition/kernel
    make clean
    make -j$(nproc) ARCH=arm64 CC=clang LLVM=1 \
      CROSS_COMPILE=aarch64-linux-gnu- \
      KDIR=$KDIR \
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
