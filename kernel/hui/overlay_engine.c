#include "hui.h"
#include <linux/kmod.h>
#include <linux/fs.h>

static int hui_run_mount_cmd(const char *lower, const char *upper, const char *work, const char *target) {
    char options[512];
    char *argv[] = { "/system/bin/mount", "-t", "overlay", "-o", options, "overlay", (char *)target, NULL };
    char *envp[] = { "HOME=/", "PATH=/sbin:/system/bin", NULL };

    snprintf(options, sizeof(options), "lowerdir=%s,upperdir=%s,workdir=%s", lower, upper, work);
    int ret = call_usermodehelper(argv[0], argv, envp, UMH_WAIT_PROC);
    if (ret == 0) {
        pr_info("HUI: 成功挂载 OverlayFS 到 %s\n", target);
    } else {
        pr_err("HUI: 挂载失败 (ret: %d)\n", ret);
    }
    return ret;
}

void hui_mount_modules(void) {
    const char *lower = "/system";
    const char *upper = "/data/adb/huijuisu/modules/module_test/system";
    const char *work = "/data/adb/huijuisu/work";
    const char *target = "/system";
    struct path upper_path;

    if (kern_path(upper, LOOKUP_FOLLOW, &upper_path) == 0) {
        path_put(&upper_path);
        hui_run_mount_cmd(lower, upper, work, target);
    }
}
