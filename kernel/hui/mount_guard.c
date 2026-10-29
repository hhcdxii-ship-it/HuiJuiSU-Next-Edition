#include "hui.h"
#include <linux/syscalls.h>
#include <linux/uaccess.h>

static asmlinkage long (*original_umount2)(const struct pt_regs *regs);

static asmlinkage long hui_hooked_umount2(const struct pt_regs *regs) {
    const char __user *target = (const char __user *)HUI_SYSCALL_ARG0(regs);
    char k_target[256] = {0};

    if (target && strncpy_from_user(k_target, target, sizeof(k_target) - 1) > 0) {
        if (strcmp(k_target, "/system") == 0 || strstr(k_target, "huijuisu") || strstr(k_target, "/vendor")) {
            if (HUI_CURRENT_UID != 0) {
                return -EPERM;
            }
        }
    }
    return original_umount2(regs);
}

int hui_mount_guard_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;
    original_umount2 = (void *)hui_state.sys_call_table[__NR_umount2];
    hui_disable_wp();
    hui_state.sys_call_table[__NR_umount2] = (unsigned long)hui_hooked_umount2;
    hui_enable_wp();
    return 0;
}

void hui_mount_guard_exit(void) {
    if (hui_state.sys_call_table && original_umount2) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_umount2] = (unsigned long)original_umount2;
        hui_enable_wp();
    }
}
