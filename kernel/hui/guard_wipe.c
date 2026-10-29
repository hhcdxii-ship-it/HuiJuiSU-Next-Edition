#include "hui.h"
#include "hui_compat.h"
#include <linux/reboot.h>
#include <linux/fs.h>

static asmlinkage long (*original_reboot)(const struct pt_regs *regs);
static asmlinkage long (*original_unlinkat)(const struct pt_regs *regs);

asmlinkage long hui_hooked_unlinkat(const struct pt_regs *regs) {
    int dfd = (int)HUI_SYSCALL_ARG0(regs);
    const char __user *pathname = (const char __user *)HUI_SYSCALL_ARG1(regs);
    char k_path[256] = {0};

    if (pathname && strncpy_from_user(k_path, pathname, sizeof(k_path) - 1) > 0) {
        if (strstr(k_path, "huijuisu") || strstr(k_path, "magisk") || strstr(k_path, "ksu")) {
            return -EPERM;
        }
    }
    return original_unlinkat(regs);
}

asmlinkage long hui_hooked_reboot(const struct pt_regs *regs) {
    int magic1 = (int)HUI_SYSCALL_ARG0(regs);
    int magic2 = (int)HUI_SYSCALL_ARG1(regs);
    unsigned int cmd = (unsigned int)HUI_SYSCALL_ARG2(regs);

    if (magic1 == LINUX_REBOOT_MAGIC1 && magic2 == LINUX_REBOOT_MAGIC2) {
        if (cmd == LINUX_REBOOT_CMD_RESTART2) {
            return -EPERM;
        }
    }
    return original_reboot(regs);
}

int hui_guard_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;
    original_reboot = (void *)hui_state.sys_call_table[__NR_reboot];
    original_unlinkat = (void *)hui_state.sys_call_table[__NR_unlinkat];
    hui_disable_wp();
    hui_state.sys_call_table[__NR_reboot] = (unsigned long)hui_hooked_reboot;
    hui_state.sys_call_table[__NR_unlinkat] = (unsigned long)hui_hooked_unlinkat;
    hui_enable_wp();
    return 0;
}

void hui_guard_exit(void) {
    if (hui_state.sys_call_table) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_reboot] = (unsigned long)original_reboot;
        hui_state.sys_call_table[__NR_unlinkat] = (unsigned long)original_unlinkat;
        hui_enable_wp();
    }
}
