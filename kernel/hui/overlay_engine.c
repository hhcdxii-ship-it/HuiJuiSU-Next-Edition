// ==========================================
// 🚀 HUI 终极内核架构 - OverlayFS 挂载引擎
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/kmod.h>
#include <linux/fs.h>

// 使用 call_usermodehelper 运行 Android 的 mount 命令
int hui_run_mount_cmd(const char *lower, const char *upper, const char *work, const char *target) {
    char options[512];
    
    // 组合 OverlayFS 挂载参数
    snprintf(options, sizeof(options), "lowerdir=%s,upperdir=%s,workdir=%s", lower, upper, work);

    // 在 Android 上，mount 通常位于 /system/bin/mount
    char *argv[] = {
        "/system/bin/mount",
        "-t", "overlay",
        "-o", options,
        "overlay",
        (char *)target,
        NULL
    };
    char *envp[] = { "HOME=/", "PATH=/sbin:/system/sbin:/system/bin:/system/xbin", NULL };

    pr_info("HUI: 准备执行内核态挂载: %s\n", options);

    // UMH_WAIT_PROC 表示等待命令执行完毕并返回结果
    int ret = call_usermodehelper(argv[0], argv, envp, UMH_WAIT_PROC);
    if (ret == 0) {
        pr_info("HUI: 成功挂载 OverlayFS 到 %s\n", target);
    } else {
        pr_err("HUI: 挂载失败 (ret: %d)\n", ret);
    }
    return ret;
}

// 遍历模块并挂载
void hui_mount_modules(void) {
    // 这是一个示例，真实工程需要遍历 /data/adb/huijuisu/modules 下的每一个模块
    const char *lower = "/system";
    const char *upper = "/data/adb/huijuisu/modules/module_test/system";
    const char *work = "/data/adb/huijuisu/work";
    const char *target = "/system";

    struct path upper_path;
    // 先检查 upperdir 是否存在，不存在说明没有模块，直接跳过
    if (kern_path(upper, LOOKUP_FOLLOW, &upper_path) == 0) {
        path_put(&upper_path);
        pr_info("HUI: 检测到模块目录，正在执行 Overlay 合并...\n");
        
        // 确保工作目录存在
        // 实际工程中应在 Java 端或 init.rc 中创建
        hui_run_mount_cmd(lower, upper, work, target);
    } else {
        pr_info("HUI: 未检测到有效模块目录，跳过 Overlay。\n");
    }
}
