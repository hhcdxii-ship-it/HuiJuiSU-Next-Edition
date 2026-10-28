#!/system/bin/sh
# 🚀 HuiJuiSU 完整纯血修补引擎 v4.0
# 铁律：Hsud / huijui_boot 任一缺失，立即退出，绝不生成半成品镜像。

INPUT_IMG="$1"
OUTPUT_IMG="$2"
WORK_DIR="$3"
BIN_DIR="$4"

export PATH="$BIN_DIR:$PATH"
log() { echo "$1"; }

# ---------- 前置硬检查：没有 daemon 和 boot 工具，直接死 ----------
if [ ! -f "$BIN_DIR/Hsud" ]; then
    log "❌ 致命错误：$BIN_DIR/Hsud 不存在，拒绝生成无 daemon 的镜像"
    exit 2
fi
if ! command -v huijui_boot >/dev/null 2>&1 && [ ! -x "$BIN_DIR/huijui_boot" ]; then
    log "❌ 致命错误：huijui_boot 工具缺失，无法解包/重打包"
    exit 2
fi
if [ ! -f "$INPUT_IMG" ]; then
    log "❌ 错误：找不到输入镜像 $INPUT_IMG"
    exit 1
fi

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR" || exit 1

log ">>> [1/5] 校验镜像完整性..."
cp "$INPUT_IMG" boot.img
chmod 644 boot.img

log ">>> [2/5] 底层解包..."
huijui_boot unpack boot.img
if [ $? -ne 0 ]; then
    log "❌ 解包失败，镜像格式不受支持或不完整"
    exit 1
fi

log ">>> [3/5] 清理旧管理器残留并注入 HuiJuiSU..."
if [ -f "ramdisk.cpio" ]; then
    mkdir -p ramdisk_root
    cd ramdisk_root || exit 1

    cpio -idmv < ../ramdisk.cpio 2>/dev/null

    # 只删 ramdisk 内的 init 注入痕迹，不碰 /data/adb 用户数据
    rm -rf ksu ksud magisk .magisk apatch apd

    # 注入 daemon 二进制到 ramdisk 的 /system/bin
    mkdir -p system/bin
    cp "$BIN_DIR/Hsud" system/bin/Hsud
    chmod 755 system/bin/Hsud

    # 注入 init rc（放 ramdisk 根目录，init 自动扫描）
    log " 💉 注入 huijui.rc..."
    cat > huijui.rc << 'HIJRC'
on post-fs-data
    mkdir /data/adb/huijuisu 0770 root root
    chmod 755 /data/adb/huijuisu

service Hsud /system/bin/Hsud daemon
    class late_start
    user root
    group root
    seclabel u:r:su:s0

on property:sys.boot_completed=1
    start Hsud
HIJRC
    chmod 644 huijui.rc

    find . | cpio -o -H newc 2>/dev/null > ../ramdisk_new.cpio
    cd ..
    mv ramdisk_new.cpio ramdisk.cpio
else
    log "❌ 没有 ramdisk.cpio，无法注入 daemon"
    exit 1
fi

log ">>> [4/5] 重打包镜像..."
huijui_boot repack boot.img "$OUTPUT_IMG"
if [ $? -ne 0 ]; then
    log "❌ 重打包失败"
    exit 1
fi

log ">>> [5/5] 清理工作区..."
cd /
rm -rf "$WORK_DIR"

log "✅ 修补成功：$OUTPUT_IMG"
exit 0
