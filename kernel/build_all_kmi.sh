#!/usr/bin/env bash
# =============================================================================
# 分 KMI 构建 HUI 内核模块
#
# 这条链路是**实测跑通**的（2026-11-01，本地 6.12.96 树），步骤不能省：
#
#   1. 准备内核树（只需一次，之后复用 O= 目录）
#        make -C <tree> O=<out> ARCH=arm64 LLVM=1 CC=clang gki_defconfig
#        make -C <tree> O=<out> ARCH=arm64 LLVM=1 CC=clang modules_prepare -j8
#
#   2. 编出 vmlinux —— 这一步才是关键。
#      没有 vmlinux 就没有 vmlinux.symvers，模块的 MODVERSIONS CRC 会全部缺失，
#      产物看着正常、insmod 却必然报 "disagrees about version of symbol"。
#        make -C <tree> O=<out> ARCH=arm64 LLVM=1 CC=clang vmlinux -j8
#
#   3. 带 symvers 编外部模块
#        make -C <tree> M=<repo>/kernel/src O=<out> ARCH=arm64 LLVM=1 CC=clang \
#             KBUILD_EXTRA_SYMBOLS=<out>/vmlinux.symvers modules -j8
#
# 产物只有两个：huijui_phantom.ko（引擎，装进 ramdisk）与 temp_root.ko（幽灵模式）。
#
# 用法：
#   KMI_612_TREE=~/android-kernel/common KMI_612_OUT=/tmp/gki_out ./kernel/build_all_kmi.sh
#   未提供的分支会被跳过。
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO/kernel/src"
DEST="$REPO/kernel/outputs_built"

declare -A TREES=(
    ["android12-5.10"]="${KMI_510_TREE:-}"
    ["android13-5.10"]="${KMI_510_TREE:-}"
    ["android13-5.15"]="${KMI_515_TREE:-}"
    ["android14-5.15"]="${KMI_515_TREE:-}"
    ["android14-6.1"]="${KMI_61_TREE:-}"
    ["android15-6.6"]="${KMI_66_TREE:-}"
    ["android16-6.12"]="${KMI_612_TREE:-}"
    ["android17-6.18"]="${KMI_618_TREE:-}"
)

built=0
skipped=0
failed=0
mkdir -p "$DEST"

for branch in "${!TREES[@]}"; do
    tree="${TREES[$branch]}"
    kernel_ver="${branch#*-}"

    if [ -z "$tree" ] || [ ! -d "$tree" ]; then
        echo "[skip]  $branch：未提供内核树（KMI_*_TREE）"
        skipped=$((skipped + 1))
        continue
    fi
    if [ ! -f "$tree/include/generated/autoconf.h" ]; then
        echo "[skip]  $branch：$tree 尚未 prepare"
        skipped=$((skipped + 1))
        continue
    fi

    echo "[build] $branch  ←  $tree"

    rm -f "$SRC"/*.ko "$SRC"/*.o "$SRC"/.*.cmd "$SRC/Module.symvers" "$SRC/modules.order" 2>/dev/null || true

    if ! make -C "$tree" M="$SRC" ARCH=arm64 LLVM=1 CC=clang \
             KBUILD_EXTRA_SYMBOLS="$tree/vmlinux.symvers" modules -j8 \
             >/tmp/kbuild_"$branch".log 2>&1; then
        echo "  ✗ 编译失败，日志：/tmp/kbuild_$branch.log"
        failed=$((failed + 1))
        continue
    fi

    for ko in "$SRC"/huijui_phantom.ko; do
        [ -f "$ko" ] || continue
        if strings -a "$ko" | grep -q "^vermagic=$kernel_ver\."; then
            out="$DEST/HuiJuiPhantom_${branch}_arm64.ko"
            cp "$ko" "$out"
            printf '  ✓ %-46s %8s B\n' "$(basename "$out")" "$(stat -c %s "$out")"
        else
            echo "  ✗ vermagic 与 $kernel_ver 不符，拒绝落位"
            failed=$((failed + 1))
        fi
    done

    built=$((built + 1))
done

echo
echo "构建分支 $built 个 / 跳过 $skipped 个 / 异常 $failed 处"
echo "产物目录：$DEST"
echo "⚠️ 落位到 assets 前请先在真机验证可加载，再："
echo "   cp $DEST/HuiJuiPhantom_<branch>_arm64.ko $REPO/app/src/main/assets/exploit/"
