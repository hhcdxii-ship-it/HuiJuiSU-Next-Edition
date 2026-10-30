#!/bin/bash

# 🚀 补充缺失的 3 个 GKI 版本
BRANCHES=(
    "android13-5.10"
    "android14-5.15"
    "android17-6.18"
)

WORK_DIR=~/android-kernel
mkdir -p $WORK_DIR

for BRANCH in "${BRANCHES[@]}"; do
    echo "======================================================"
    echo "🚀 开始处理缺失版本: $BRANCH"
    echo "======================================================"
    
    SHORT_NAME=$(echo $BRANCH | sed 's/common-//')
    KDIR="$WORK_DIR/$SHORT_NAME"
    mkdir -p $KDIR
    cd $KDIR

    echo "🔄 1. 拉取 $BRANCH 源码 (浅克隆)..."
    git clone --depth 1 -b $BRANCH https://android.googlesource.com/kernel/common .
    
    if [ $? -ne 0 ]; then
        echo "❌ 拉取 $BRANCH 失败，跳过该版本。"
        continue
    fi

    echo "⚙️ 2. 配置编译环境..."
    make -j6 ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- gki_defconfig
    make -j6 ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- modules_prepare

    echo "🔨 3. 编译 HuiJuiPhantom.ko..."
    cd ~/HuiJuiSU-Next-Edition/kernel
    make clean
    make -j6 ARCH=arm64 CC=clang LLVM=1 \
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

    echo "🧹 4. 清理源码释放空间..."
    cd ~
    rm -rf $KDIR
done

echo "🎉 缺失的 3 个版本全部补充完毕！"
