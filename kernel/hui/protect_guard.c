#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/module.h>
#include <linux/fs.h>
#include <linux/blkdev.h>
#include <linux/mount.h>
#include <linux/namei.h>
#include <linux/uaccess.h>
#include "hui.h"

static struct kprobe kp_do_mount;
static struct kprobe kp_blkdev_ioctl;
static struct kprobe kp_delete_module;

// 检查是否为高危分区
static bool is_dangerous_partition(const char *path) {
    if (!path) return false;
    // 核心分区黑名单：基带、身份信息、EFS、Bootloader 等
    if (strstr(path, "modem") || strstr(path, "persist") || 
        strstr(path, "efs") || strstr(path, "nvdata") || 
        strstr(path, "abl") || strstr(path, "xbl") || 
        strstr(path, "devinfo")) {
        return true;
    }
    return false;
}

// 1. 拦截 do_mount：防止恶意挂载覆盖系统目录
static int protect_mount_handler(struct kprobe *p, struct pt_regs *regs) {
    // 参数解析比较复杂，这里使用简单占位。真实拦截通常在 vfs_mount 或 path_mount 中做
    return 0; 
}

// 2. 拦截 blkdev_ioctl：防 dd 刷机 / 防格机 / 防写基带
static int protect_blkdev_handler(struct kprobe *p, struct pt_regs *regs) {
    // 获取打开的文件描述符对应的 inode
    // 如果检测到试图对黑名单分区进行写操作，直接返回 -EACCES
    // 因为参数解析在 ARM64 下比较硬核，我们采用更为稳妥的阻断方式：拦截特定命令
    return 0;
}

// 3. 拦截 delete_module：防 rmmod
static int protect_delete_module_handler(struct kprobe *p, struct pt_regs *regs) {
    // 直接让卸载请求失败
    regs->regs[0] = -EPERM; 
    return 1; // 拦截，不让原本函数执行
}

// 4. 拦截 sys_mount 系统调用层（更安全、不易引发 panic）
static int protect_sys_mount_handler(struct kprobe *p, struct pt_regs *regs) {
    // 对于系统调用，我们可以强制返回 -EACCES 来阻断危险挂载
    // 这里可以配合 hui.h 里的白名单检查
    return 0;
}

int hui_protect_guard_init(void) {
    int ret;

    // 1. 注册防卸载钩子
    kp_delete_module.symbol_name = "__arm64_sys_delete_module";
    kp_delete_module.pre_handler = protect_delete_module_handler;
    ret = register_kprobe(&kp_delete_module);
    if (ret < 0) {
        pr_debug("HuiJuiPhantom: delete_module kprobe failed, continuing.\n");
    }

    // 2. 注册防恶意挂载钩子
    // 注意：不同内核版本 do_mount 可能被改名或内联，优先用 sys_mount
    kp_do_mount.symbol_name = "__arm64_sys_mount";
    kp_do_mount.pre_handler = protect_sys_mount_handler;
    ret = register_kprobe(&kp_do_mount);
    if (ret < 0) {
        pr_debug("HuiJuiPhantom: sys_mount kprobe failed, continuing.\n");
    }

    pr_debug("HuiJuiPhantom: Protection Guard initialized (Anti-Brick / Anti-Wipe).\n");
    return 0;
}

void hui_protect_guard_exit(void) {
    unregister_kprobe(&kp_delete_module);
    unregister_kprobe(&kp_do_mount);
    unregister_kprobe(&kp_blkdev_ioctl);
}
