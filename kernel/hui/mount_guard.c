// ==========================================
// 🚀 HUI 终极内核架构 - 35/56：挂载点防卸载保护
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/syscalls.h>
#include <linux/uaccess.h>

static asmlinkage long (*original_umount2)(const struct pt_regs *regs);
static __attribute__((unused)) 
asmlinkage long hui_hooked_umount2(const struct pt_regs *regs) {
    char k_target[256] = {0};
    const char __user *target = (const char __user *)HUI_SYSCALL_ARG0(regs);

    if (target && strncpy_from_user(k_target, target, sizeof(k_target) - 1) > 0) {
        // 如果目标是我们的 OverlayFS 挂载点，直接拦截
        if (strcmp(k_target, "/system") == 0 || strstr(k_target, "huijuisu") || strstr(k_target, "/vendor")) {
            // 允许管理器自己卸载（这里可以配合白名单 UID 判断），否则拦截
static __attribute__((unused))   if(HUI_CURRENT_UID != 0) {
                pr_warn("HUI: 拦截到针对 %s 的恶意卸载请求！\n", k_target);
                return -EPERM;
            }
        }
    }

    return original_umount2(regs);
}
static __attribute__((unused)) 
int hui_mount_guard_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;

    original_umount2 = (void *)hui_state.sys_call_table[__NR_umount2];

    hui_disable_wp();
    hui_state.sys_call_table[__NR_umount2] = (unsigned long)hui_hooked_umount2;
    hui_enable_wp();

    pr_info("HUI: 挂载点防卸载防护已启动！\n");
    return 0;
}
static __attribute__((unused)) 
void hui_mount_guard_exit(void) {
static __attribute__((unused))   if(hui_state.sys_call_table && original_umount2) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_umount2] = (unsigned long)original_umount2;
        hui_enable_wp();
        pr_info("HUI: 挂载点防卸载已关闭。\n");
    }
}
