#!/system/bin/sh
echo "=== HUI 内核验证 ==="
if [ -c /dev/huijui ]; then
    echo "[✅] /dev/huijui 存在"
    cat /dev/huijui
else
    echo "[❌] /dev/huijui 不存在，内核模块未加载！"
fi
