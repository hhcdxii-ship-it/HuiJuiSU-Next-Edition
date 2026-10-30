#!/bin/bash

# 🚀 针对老战神（非 GKI，如小米6）的编译脚本
# 假设你已经把小米6的 4.4 源码放到了 ~/android-kernel/mi6-4.4
KDIR_MI6=~/android-kernel/mi6-4.4

if [ ! -d "$KDIR_MI6" ]; then
    echo "❌ 找不到小米6源码树 $KDIR_MI6，请先手动把源码放到这个目录！"
    exit 1
fi

echo "======================================================"
echo "🚀 开始编译老战神专属内核 (arm64 非GKI)"
echo "======================================================"

cd ~/HuiJuiSU-Next-Edition/kernel
make clean

# 针对 4.4 内核，通常不能用 LLVM=1，必须用特定的 GCC 交叉编译器
# 这里假设你用的是标准的 aarch64-linux-gnu-gcc
echo "🔨 3. 编译 HuiJuiPhantom.ko..."
make ARCH=arm64 CC=aarch64-linux-gnu-gcc \
  CROSS_COMPILE=aarch64-linux-gnu- \
  KDIR=$KDIR_MI6 \
  modules KBUILD_MODPOST_WARN=1

if [ -f "HuiJuiPhantom.ko" ]; then
    echo "✅ 编译成功，正在瘦身并改名..."
    aarch64-linux-gnu-strip --strip-debug HuiJuiPhantom.ko
    cp HuiJuiPhantom.ko ../app/src/main/assets/exploit/HuiJuiPhantom_legacy-4.4_arm64.ko
    echo "📦 已保存至 assets: HuiJuiPhantom_legacy-4.4_arm64.ko"
else
    echo "❌ 编译失败！老内核可能需要对 API 做大量降级适配。"
fi
