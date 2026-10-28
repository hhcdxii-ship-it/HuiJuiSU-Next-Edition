#!/system/bin/sh
# 🚀 HuiJuiSU 极速热接管引擎 v5（防砖终极版）
WORK_DIR="$1"
ASSET_DIR="$2"
KO_NAME="$3"
log() { echo "$1"; }
HIJ_DIR=/data/adb/huijuisu

log ">>> [1/7] 准备 HuiJuiSU 运行目录..."
mkdir -p "$HIJ_DIR"
chmod 755 "$HIJ_DIR"

log ">>> [2/7] 释放 daemon 二进制与内核模块..."
if [ -f "$ASSET_DIR/Hsud" ] && [ -f "$ASSET_DIR/$KO_NAME" ]; then
    cp "$ASSET_DIR/Hsud" "$HIJ_DIR/Hsud"
    cp "$ASSET_DIR/$KO_NAME" "$HIJ_DIR/$KO_NAME"
    chmod 755 "$HIJ_DIR/Hsud"
else
    log "❌ 错误：assets 里找不到 Hsud 或 $KO_NAME"
    exit 1
fi

log ">>> [3/7] 拉起 HuiJuiSU 守护进程（先起自己）..."
OLD_PID=$(cat "$HIJ_DIR/.pid" 2>/dev/null)
[ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null && kill "$OLD_PID" 2>/dev/null
rm -f "$HIJ_DIR/.active" "$HIJ_DIR/.pid"

setsid "$HIJ_DIR/Hsud" daemon >/dev/null 2>&1 &
sleep 1
NEW_PID=$(cat "$HIJ_DIR/.pid" 2>/dev/null)
if [ -z "$NEW_PID" ] || ! kill -0 "$NEW_PID" 2>/dev/null; then
    log "❌ 致命：HuiJuiSU daemon 启动失败！"
    exit 1
fi
log " ✅ HuiJuiSU daemon 已启动 PID=$NEW_PID"

log ">>> [4/7] 注入 HUI 纯血内核模块..."
# 🚀 核心：防砖安全锁
if lsmod | grep -q HUI; then
    log " ⚠️ HUI 内核模块已加载，无需重复注入"
else
    insmod "$HIJ_DIR/$KO_NAME" 2>&1
    INS_ERR=$?
    if [ $INS_ERR -eq 0 ]; then
        log " ✅ HUI 内核模块加载成功！"
    else
        log " ❌ 内核模块加载失败 (code $INS_ERR)，正在尝试紧急卸弹以防死机..."
        rmmod HUI 2>/dev/null
        dmesg | tail -n 5
        log " ⚠️ 已执行紧急卸载，系统安全。请回 App 查看报错详情。"
    fi
fi

log ">>> [5/7] 停止旧管理器进程..."
for name in ksud magiskd apd; do
    if pidof "$name" >/dev/null 2>&1; then
        log "  停止 $name ..."
        killall "$name" 2>/dev/null
    fi
done

log ">>> [6/7] 验证接管状态..."
sleep 1
if [ -f "$HIJ_DIR/.active" ] && kill -0 "$NEW_PID" 2>/dev/null; then
    log "✅ 热接管成功！HuiJuiSU 已在工作状态。"
    exit 0
else
    log "⚠️ 接管完成但自证失败，请回 App 查看状态。"
    exit 1
fi
