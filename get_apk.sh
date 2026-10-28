#!/bin/bash
echo "🔍 正在通过 API 获取最新 APK 下载地址..."
URL=$(curl -s https://api.github.com/repos/hhcdxii-ship-it/HuiJuiSU-Next-Edition/actions/artifacts | grep "archive_download_url" | head -n 1 | cut -d '"' -f 4)

if [ -z "$URL" ]; then
    echo "❌ 没找到 APK 包，可能还在编译中，等一两分钟再跑一次！"
    exit 1
fi

echo "📥 正在下载..."
curl -L -o app.zip -H "Accept: application/vnd.github+json" "$URL"

echo "📦 正在解压..."
unzip -o app.zip -d apk_output
rm app.zip

echo "✅ 下载完成！已为您自动打开 Windows 文件夹！"
# 直接调用 Windows 资源管理器打开该文件夹
explorer.exe apk_output
