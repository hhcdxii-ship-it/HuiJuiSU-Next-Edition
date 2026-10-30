#!/bin/bash
BRANCH="android17-6.18"
WORK_DIR=~/android-kernel
KDIR="$WORK_DIR/$BRANCH"
mkdir -p $KDIR
cd $KDIR

echo "🔄 拉取 $BRANCH 源码..."
git clone --depth 1 -b $BRANCH https://android.googlesource.com/kernel/common .

echo "⚙️ 配置编译环境..."
make -j6 ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- gki_defconfig
make -j6 ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- modules_prepare

echo "🔨 编译 HuiJuiPhantom.ko..."
cd ~/HuiJuiSU-Next-Edition/kernel
make clean
make -j6 ARCH=arm64 CC=clang LLVM=1 \
  CROSS_COMPILE=aarch64-linux-gnu- \
  KDIR=$KDIR \
  KBUILD_MODPOST_WARN=1

if [ -f "HuiJuiPhantom.ko" ]; then
    llvm-strip --strip-debug HuiJuiPhantom.ko
    cp HuiJuiPhantom.ko ../app/src/main/assets/exploit/HuiJuiPhantom_android17-6.18_arm64.ko
    echo "📦 已保存至 assets: HuiJuiPhantom_android17-6.18_arm64.ko"
else
    echo "❌ 6.18 编译失败！"
fi

rm -rf $KDIR
