#!/bin/bash
# 🚀 HuiJuiSU 纯血 Daemon 编译脚本
# 依赖：Android NDK。如果你没有，去 Android Studio 的 SDK Manager 里下个 NDK。

echo ">>> 正在查找 NDK 路径..."
# 自动探测常见的 WSL/Linux 下的 NDK 路径
NDK_BASE=$(ls -d $HOME/Android/Sdk/ndk/* 2>/dev/null | sort -V | tail -n 1)
if [ -z "$NDK_BASE" ]; then
    NDK_BASE=$(ls -d $HOME/Android/Sdk/ndk-bundle 2>/dev/null)
fi

if [ -z "$NDK_BASE" ]; then
    echo "❌ 找不到 NDK！请确保安装了 NDK，或者手动修改此脚本中的 NDK_BASE 变量。"
    exit 1
fi
echo " ✅ 找到 NDK: $NDK_BASE"

# 设置交叉编译器
CC="$NDK_BASE/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang"

if [ ! -f "$CC" ]; then
    echo "❌ 找不到编译器 $CC"
    exit 1
fi

# 编译 C 源码，输出为静态链接的可执行文件
echo ">>> 正在编译 Hsud.c ..."
$CC -O2 -static -s -o app/src/main/assets/Hsud Hsud.c

if [ $? -eq 0 ]; then
    echo "✅ 编译成功！已生成 ARM64 静态二进制: app/src/main/assets/Hsud"
    chmod +x app/src/main/assets/Hsud
    file app/src/main/assets/Hsud
else
    echo "❌ 编译失败！"
    exit 1
fi
