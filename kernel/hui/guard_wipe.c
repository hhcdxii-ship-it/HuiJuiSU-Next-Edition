// ==========================================
// 🚀 HUI 终极内核架构 - 防格机与清数据拦截
// ==========================================
#include "hui.h"

static asmlinkage long (*original_reboot)(const struct pt_regs *regs);
static asmlinkage long (*original_unlinkat)(const struct pt_regs *regs);

// 🚀 核心防护：拦截删除操作 (unlinkat)
static asmlinkage long hui_hooked_unlinkat(const struct pt_regs *regs) {
    const char __user *pathname = (const char __user *)HUI_SYSCALL_ARG1(regs);
    char k_path[256] = {0};

    if (pathname) {
        long ret = strncpy_from_user(k_path, pathname, sizeof(k_path) - 1);
        if (ret > 0) {
            // 拦截对 HUI 根目录、系统核心分区的删除
            if (strstr(k_path, "huijuisu") || strstr(k_path, "magisk") || strstr(k_path, "ksu")) {
                pr_warn("HuiJuiPhantom: 拦截到危险删除指令！目标: %s\n", k_path);
                return -EPERM;
            }
        }
    }
    return original_unlinkat(regs);
}

// 🚀 核心防护：拦截非法重启 (reboot)
static asmlinkage long hui_hooked_reboot(const struct pt_regs *regs) {
    int magic1 = (int)HUI_SYSCALL_ARG0(regs);
    int magic2 = (int)HUI_SYSCALL_ARG1(regs);
    unsigned int cmd = (unsigned int)HUI_SYSCALL_ARG2(regs);

    // 如果尝试重启进 recovery 或 bootloader，直接拦截（防止恶意清数据）
    if (magic1 == LINUX_REBOOT_MAGIC1 && magic2 == LINUX_REBOOT_MAGIC2) {
        if (cmd == LINUX_REBOOT_CMD_RESTART2) {
            pr_warn("HuiJuiPhantom: 拦截到强行重启至 Recovery/Bootloader 的请求！\n");
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

    pr_info("HuiJuiPhantom: 防格机安全防护盾已启动！\n");
    return 0;
}

void hui_guard_exit(void) {
    if (hui_state.sys_call_table) {
        hui_disable_wp();
        if (original_reboot) hui_state.sys_call_table[__NR_reboot] = (unsigned long)original_reboot;
        if (original_unlinkat) hui_state.sys_call_table[__NR_unlinkat] = (unsigned long)original_unlinkat;
        hui_enable_wp();
        pr_info("HuiJuiPhantom: 防格机安全防护盾已解除。\n");
    }
}
