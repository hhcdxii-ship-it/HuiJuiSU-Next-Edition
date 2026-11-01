#!/usr/bin/env bash
# =============================================================================
# 按 KMI 分支构建 HUI 内核模块，并校验 vermagic 与分支一致
#
# 背景：仓库里 8 个 .ko 是分版本构建出来的，但整条链路没有脚本化，
#       曾经出现过「8 个文件里 7 个是同一个 5.10 二进制改名」的回归，
#       运行期的表现是「刷入成功但状态一直未工作」，极难排查。
#
# 用法：
#   KMI_510_TREE=~/kernels/5.10/common \
#   KMI_515_TREE=~/kernels/5.15/common \
#   KMI_61_TREE=~/kernels/6.1/common   \
#   KMI_66_TREE=~/kernels/6.6/common   \
#   KMI_612_TREE=~/kernels/6.12/common \
#   KMI_618_TREE=~/kernels/6.18/common \
#   ./kernel/build_all_kmi.sh
#
#   未提供树的分支会被跳过（不会误产物）。
#   每棵树必须提前 prepare 过：存在 include/generated/autoconf.h。
#
# ⚠️ 本脚本只负责「编译 + 校验 + 报告」，不负责把多个 obj-m 合成单个 .ko ——
#    内核模块不能靠 cat 拼接，合成方式必须沿用你们现有的打包步骤。
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO/kernel/src"

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

for branch in "${!TREES[@]}"; do
    tree="${TREES[$branch]}"
    kernel_ver="${branch#*-}"

    if [ -z "$tree" ] || [ ! -d "$tree" ]; then
        echo "[skip]  $branch：未提供内核树（KMI_*_TREE）"
        skipped=$((skipped + 1))
        continue
    fi
    if [ ! -f "$tree/include/generated/autoconf.h" ]; then
        echo "[skip]  $branch：$tree 尚未 prepare（缺 include/generated/autoconf.h）"
        skipped=$((skipped + 1))
        continue
    fi

    echo "[build] $branch  ←  $tree"

    if ! make -C "$tree" M="$SRC" ARCH=arm64 LLVM=1 CC=clang \
             CROSS_COMPILE=aarch64-linux-gnu- modules >/tmp/kbuild_"$branch".log 2>&1; then
        echo "  ✗ 编译失败，日志：/tmp/kbuild_$branch.log"
        failed=$((failed + 1))
        continue
    fi

    for ko in "$SRC"/*.ko; do
        [ -f "$ko" ] || continue
        name="$(basename "$ko")"
        if strings -a "$ko" | grep -q "^vermagic=$kernel_ver\."; then
            printf '  ✓ %-22s %8s B\n' "$name" "$(stat -c %s "$ko")"
        else
            printf '  ✗ %-22s vermagic 与 %s 不符\n' "$name" "$kernel_ver"
            failed=$((failed + 1))
        fi
    done

    built=$((built + 1))
done

echo
echo "构建分支 $built 个 / 跳过 $skipped 个 / 异常 $failed 处"
echo "产物落位后请跑：./gradlew :app:verifyKoAssets"
