#!/bin/bash

echo "🚀 [1/4] 正在跳过网络，强制配置 6.12 内核源码树..."
cd ~/android-kernel/common

# 既然已在 6.12 分支，直接强制清理并生成配置文件
make distclean 2>/dev/null
# 生成 GKI 的默认配置文件
make ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- gki_defconfig
# 准备外部模块编译所需的头文件
make ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- modules_prepare

echo "🔨 [2/4] 正在编译纯血 HUI 内核模块..."
cd ~/HuiJuiSU-Next-Edition/kernel
# 暴力清理当前目录的编译产物
find . -name "*.o" -delete
find . -name "*.ko" -delete

# 直接调用内核的 M= 参数编译咱们的纯血模块
make -C ~/android-kernel/common M=$(pwd) ARCH=arm64 LLVM=1 CC=clang \
  CROSS_COMPILE=aarch64-linux-gnu- KBUILD_MODPOST_WARN=1 modules -j$(nproc)

if [ ! -f "HuiJuiPhantom.ko" ]; then
    echo "❌ 模块编译失败！请在终端把上面的报错复制给我。"
    exit 1
fi

echo "📦 [3/4] 正在把生成的 .ko 塞进 App 资源库..."
cd ~/HuiJuiSU-Next-Edition
cp kernel/HuiJuiPhantom.ko app/src/main/assets/exploit/kernelsu.ko

if [ -f ~/ksu_peek/out/init ]; then
    cp ~/ksu_peek/out/init app/src/main/assets/exploit/huijuiinit
fi

echo "📱 [4/4] 正在打包最终的发布级 APK..."
./gradlew assembleDebug

echo "======================================================"
echo "🎉 大功告成！所有底层资源已自动打包进 APK！"
echo "👉 最终 APK 位于: app/build/outputs/apk/debug/HuiJuiSU-Next-Edition-v2-debug.apk"
echo "======================================================"
