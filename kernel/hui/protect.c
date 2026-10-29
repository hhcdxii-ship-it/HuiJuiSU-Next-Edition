// ==========================================
// 🚀 HUI 终极内核架构 - 20/56：防卸载拦截 (防砖)
// ==========================================
#include "hui.h"
#include <linux/syscalls.h>
#include <linux/fdtable.h>

// 原始 delete_module 指针
static asmlinkage long (*original_delete_module)(const struct pt_regs *regs);

// 劫持 delete_module
static asmlinkage long hui_hooked_delete_module(const struct pt_regs *regs) {
    char k_name[256] = {0};
    const char __user *name_user = (const char __user *)regs->regs[0];

    // 从用户态安全拷贝模块名
    if (strncpy_from_user(k_name, name_user, sizeof(k_name) - 1) < 0) {
        return -EFAULT;
    }

    // 🚨 防砖保护：如果有人想卸载 HUI，直接拒绝，防止系统崩溃
    if (strcmp(k_name, "HUI") == 0) {
        pr_warn("HUI: 拦截到恶意卸载请求！模块已锁定，防止系统死机。\n");
        return -EPERM; // 返回权限不足，拒绝卸载
    }

    // 其他模块正常放行
    return original_delete_module(regs);
}

// 注册防卸载 Hook（在主模块初始化时调用）
int hui_protect_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;

    original_delete_module = (void *)hui_state.sys_call_table[__NR_delete_module];

    hui_disable_wp();
    hui_state.sys_call_table[__NR_delete_module] = (unsigned long)hui_hooked_delete_module;
    hui_enable_wp();

    pr_info("HUI: 防砖保护盾已开启，模块无法被恶意卸载。\n");
    return 0;
}

void hui_protect_exit(void) {
    if (hui_state.sys_call_table && original_delete_module) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_delete_module] = (unsigned long)original_delete_module;
        hui_enable_wp();
        pr_info("HUI: 防砖保护盾已解除。\n");
    }
}
