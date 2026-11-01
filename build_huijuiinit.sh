#!/bin/bash
echo "🔍 正在寻找 Android NDK..."
NDK_PATH=""
for path in ~/Android/Sdk/ndk/* /mnt/c/Users/Admin/AppData/Local/Android/Sdk/ndk/* /mnt/d/Android/Sdk/ndk/*; do
    if [ -d "$path" ]; then
        NDK_PATH=$path
        break
    fi
done

NDK_PATH=$(ls -d $NDK_PATH | sort -V | tail -n 1)
echo "✅ 找到 NDK: $NDK_PATH"

CLANG_WIN="$NDK_PATH/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android31-clang.cmd"
if [ ! -f "$CLANG_WIN" ]; then
    echo "❌ 找不到 Windows 版编译器！"
    exit 1
fi

WIN_BUILD_DIR="/mnt/d/adb/huijui_build"
mkdir -p $WIN_BUILD_DIR
cp core/root/native/huijuiinit/huijuiinit.c $WIN_BUILD_DIR/

# 💡 核心大招：生成一个 bat 脚本，让 Windows 自己去跑，彻底避开转义问题
cat > $WIN_BUILD_DIR/build.bat << BATEOF
@echo off
cd /d D:\adb\huijui_build
D:\Android\Sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android31-clang.cmd -static -Os -s -o huijuiinit.exe huijuiinit.c
BATEOF

# 把 bat 里的 NDK 路径替换成当前实际的路径
CMD_CLANG_WIN=$(wslpath -w "$CLANG_WIN" | sed 's/\\/\\\\/g')
sed -i "s|D:\\Android\\Sdk\\ndk\\28.2.13676358\\toolchains\\llvm\\prebuilt\\windows-x86_64\\bin\\aarch64-linux-android31-clang.cmd|$CMD_CLANG_WIN|" $WIN_BUILD_DIR/build.bat

echo "🔨 开始静态编译 huijuiinit (调用 .bat 脚本)..."
cd $WIN_BUILD_DIR
cmd.exe /c build.bat

if [ $? -eq 0 ] && [ -f "huijuiinit.exe" ]; then
    echo "✅ 编译成功！正在搬回 WSL 项目目录..."
    cp huijuiinit.exe ~/HuiJuiSU-Next-Edition/core/root/native/huijuiinit/huijuiinit
    mkdir -p ~/HuiJuiSU-Next-Edition/app/src/main/assets/exploit
    cp huijuiinit.exe ~/HuiJuiSU-Next-Edition/app/src/main/assets/exploit/huijuiinit
    
    cd ~/HuiJuiSU-Next-Edition
    chmod +x core/root/native/huijuiinit/huijuiinit
    chmod +x app/src/main/assets/exploit/huijuiinit
    
    echo "🎉 大功告成！检查文件属性："
    file core/root/native/huijuiinit/huijuiinit
else
    echo "❌ 编译失败！请检查 build.bat 里的路径。"
    cd ~/HuiJuiSU-Next-Edition
fi
