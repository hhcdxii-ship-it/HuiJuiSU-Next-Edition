#!/bin/bash

echo "🚀 [1/5] 正在修复内核源码树的 Git 状态并切换到 Android 16-6.12..."
cd ~/android-kernel/common
# 暴力修复 git 状态
git remote remove origin 2>/dev/null
git remote add origin https://android.googlesource.com/kernel/common
# 强制拉取并切到 6.12
git fetch --depth 1 origin android16-6.12
git checkout -f android16-6.12
git clean -fdx >/dev/null 2>&1

echo "🔨 [2/5] 正在编译纯血 HUI 内核模块..."
cd ~/HuiJuiSU-Next-Edition/kernel
make clean
# 自动传入忽略未导出符号的参数进行编译
make -j$(nproc) ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- \
  KDIR=~/android-kernel/common \
  KBUILD_MODPOST_WARN=1

if [ ! -f "HuiJuiPhantom.ko" ]; then
    echo "❌ 模块编译失败！请检查内核源码"
    exit 1
fi

echo "📦 [3/5] 正在把生成的 .ko 塞进 App 资源库..."
cd ~/HuiJuiSU-Next-Edition
# 彻底覆盖，没有任何手动复制的余地
cp kernel/HuiJuiPhantom.ko app/src/main/assets/exploit/kernelsu.ko

echo "🛡️ [4/5] 正在确认官方加载器就位..."
if [ -f ~/ksu_peek/out/init ]; then
    cp ~/ksu_peek/out/init app/src/main/assets/exploit/huijuiinit
    echo "✅ 已注入 KSU 加载器！"
else
    echo "⚠️ 找不到 KSU init，请确认 ~/ksu_peek/out/init 是否存在"
fi

echo "📱 [5/5] 正在打包最终的发布级 APK..."
./gradlew assembleDebug

echo "======================================================"
echo "🎉 大功告成！所有底层资源已自动打包进 APK！"
echo "👉 最终 APK 位于: app/build/outputs/apk/debug/HuiJuiSU-Next-Edition-v2-debug.apk"
echo "======================================================"
