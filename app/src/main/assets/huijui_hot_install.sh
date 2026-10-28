#!/system/bin/sh
# 🚀 HuiJuiSU 极速热接管引擎 v3（安全加固版）
# 铁律：先起自己，再停别人；不删用户数据；setsid 脱离会话防回收。
WORK_DIR="$1"
ASSET_DIR="$2"
log() { echo "$1"; }
HIJ_DIR=/data/adb/huijuisu

log ">>> [1/5] 准备 HuiJuiSU 运行目录..."
mkdir -p "$HIJ_DIR"
chmod 755 "$HIJ_DIR"

log ">>> [2/5] 释放 daemon 二进制..."
if [ -f "$ASSET_DIR/huijui_ksud" ]; then
    cp "$ASSET_DIR/huijui_ksud" "$HIJ_DIR/huijui_ksud"
    chmod 755 "$HIJ_DIR/huijui_ksud"
else
    log "❌ 错误：assets 里找不到 huijui_ksud"
    exit 1
fi

log ">>> [3/5] 拉起 HuiJuiSU 守护进程（先起自己）..."
OLD_PID=$(cat "$HIJ_DIR/.pid" 2>/dev/null)
[ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null && kill "$OLD_PID" 2>/dev/null
rm -f "$HIJ_DIR/.active" "$HIJ_DIR/.pid"

# setsid 让 daemon 脱离当前会话/进程组，su 会话关掉也不被回收
if command -v setsid >/dev/null 2>&1; then
    setsid "$HIJ_DIR/huijui_ksud" daemon >/dev/null 2>&1 &
else
    "$HIJ_DIR/huijui_ksud" daemon >/dev/null 2>&1 &
fi
sleep 1

# 自证：不靠 $NEW_PID（setsid 后拿不到真 PID），靠 .pid 文件
NEW_PID=$(cat "$HIJ_DIR/.pid" 2>/dev/null)
if [ -z "$NEW_PID" ] || ! kill -0 "$NEW_PID" 2>/dev/null; then
    log "❌ 致命：HuiJuiSU daemon 启动失败，终止接管（未触碰其他管理器）"
    exit 1
fi
log " ✅ HuiJuiSU daemon 已启动 PID=$NEW_PID"

log ">>> [4/5] 停止旧管理器进程（只停进程，不删数据）..."
for name in ksud magiskd apd; do
    if pidof "$name" >/dev/null 2>&1; then
        log "  停止 $name ..."
        killall "$name" 2>/dev/null
    fi
done

log ">>> [5/5] 验证接管状态..."
sleep 1
if [ -f "$HIJ_DIR/.active" ] && kill -0 "$NEW_PID" 2>/dev/null; then
    log "✅ 热接管成功！HuiJuiSU 已在工作状态。"
    exit 0
else
    log "⚠️ 接管完成但自证失败，请回 App 查看状态。"
    exit 1
fi
