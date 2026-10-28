// ==========================================
// 🚀 HUI 终极内核架构 - 34/56：OverlayFS 挂载引擎
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/mount.h>
#include <linux/file.h>
#include <linux/version.h>

// 利用 path_mount 挂载 OverlayFS
static int hui_do_overlay_mount(const char *lowerdir, const char *upperdir, const char *workdir, const char *target) {
    struct path path;
    char options[512];
    int ret;

    // 组合 OverlayFS 挂载参数
    snprintf(options, sizeof(options), "lowerdir=%s,upperdir=%s,workdir=%s", lowerdir, upperdir, workdir);

    // 查找挂载点
    ret = kern_path(target, LOOKUP_FOLLOW | LOOKUP_DIRECTORY, &path);
    if (ret) {
        pr_err("HUI: 找不到挂载点 %s\n", target);
        return ret;
    }

    // 调用内核挂载（注意：不同内核版本 API 可能不同，这里用最常见的 ksys_mount / path_mount）
    // 5.10+ 通常用 path_mount，这里用 ksys_mount 兼容性更好，如果编译报错，需要改对应签名
    #if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
        ret = path_mount("overlay", &path, "overlay", 0, options);
    #else
        ret = vfs_kern_mount("overlay", 0, "overlay", options);
    #endif

    path_put(&path);

    if (ret == 0) {
        pr_info("HUI: 成功挂载 OverlayFS 到 %s\n", target);
    } else {
        pr_err("HUI: 挂载 OverlayFS 失败 (%d)\n", ret);
    }
    return ret;
}

// 遍历模块并挂载
void hui_mount_modules(void) {
    // 这是一个示例，真实工程需要遍历 /data/adb/huijuisu/modules 下的每一个模块
    // 假设存在一个测试模块 module_test，有 system 目录
    const char *lower = "/system";
    const char *upper = "/data/adb/huijuisu/modules/module_test/system";
    const char *work = "/data/adb/huijuisu/work";
    const char *target = "/system";

    struct path upper_path;
    // 先检查 upperdir 是否存在，不存在说明没有模块，直接跳过
    if (kern_path(upper, LOOKUP_FOLLOW, &upper_path) == 0) {
        path_put(&upper_path);
        pr_info("HUI: 检测到模块目录，正在执行 Overlay 合并...\n");
        hui_do_overlay_mount(lower, upper, work, target);
    } else {
        pr_info("HUI: 未检测到有效模块目录，跳过 Overlay。\n");
    }
}
