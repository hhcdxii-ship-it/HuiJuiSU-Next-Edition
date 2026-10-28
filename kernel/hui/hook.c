// ==========================================
// 🚀 HUI 终极内核架构 - 2/56：Syscall 劫持引擎
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/kallsyms.h>
#include <linux/syscalls.h>
#include <asm/ptrace.h>

// 内部函数：Kprobe 寻址 sys_call_table
static unsigned long *hui_get_sys_call_table(void) {
    struct kprobe kp = {
        .symbol_name = "kallsyms_lookup_name"
    };
    unsigned long *table = NULL;
    typedef unsigned long (*kln_t)(const char *name);
    kln_t kln;

    if (register_kprobe(&kp) < 0) {
        pr_err("HUI: Kprobe 注册失败，寻址失败！\n");
        return NULL;
    }
    kln = (kln_t) kp.addr;
    unregister_kprobe(&kp);

    if (kln) {
        table = (unsigned long *)kln("sys_call_table");
    }
    return table;
}

// ARM64 写保护控制
static inline void hui_disable_wp(void) {
    unsigned long val;
    asm volatile("mrs %0, sctlr_el1" : "=r" (val));
    val &= ~(1 << 19);
    asm volatile("msr sctlr_el1, %0" : : "r" (val));
    asm volatile("isb");
}

static inline void hui_enable_wp(void) {
    unsigned long val;
    asm volatile("mrs %0, sctlr_el1" : "=r" (val));
    val |= (1 << 19);
    asm volatile("msr sctlr_el1, %0" : : "r" (val));
    asm volatile("isb");
}

// 核心提权逻辑
static void hui_elevate_root(void) {
    struct cred *new_cred;
    if (!current->mm) return; // 排除内核线程
    new_cred = prepare_kernel_cred(NULL);
    if (new_cred) {
        commit_creds(new_cred);
        pr_info("HUI: [%s] 提权成功！\n", current->comm);
    }
}

// 伪造的 execve 系统调用
asmlinkage long hui_hooked_execve_compat(const struct pt_regs *regs) {
    uid_t uid = current_uid().val;
    if (uid != 0 && hui_is_uid_allowed(uid)) {
        hui_elevate_root();
    }
    return hui_execve_with_compat(regs);
}

// 初始化 Hook
int hui_hook_init(void) {
    hui_state.sys_call_table = hui_get_sys_call_table();
    if (!hui_state.sys_call_table) return -EFAULT;

    hui_state.original_execve = (void *)hui_state.sys_call_table[__NR_execve];

    if (hui_check_other_hooks()) {
        pr_info("HUI: 正在以兼容模式叠加 Hook。\n");
    }

    hui_disable_wp();
    hui_state.sys_call_table[__NR_execve] = (unsigned long)hui_hooked_execve;
    hui_enable_wp();

    pr_info("HUI: execve 劫持成功！\n");
    return 0;
}

// 退出 Hook
void hui_hook_exit(void) {
    if (hui_state.sys_call_table && hui_state.original_execve) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_execve] = (unsigned long)hui_state.original_execve;
        hui_enable_wp();
        pr_info("HUI: execve 已恢复原状。\n");
    }
}
