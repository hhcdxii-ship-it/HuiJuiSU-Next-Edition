#include "hui.h"

// 利用 Kprobe 获取 sys_call_table 地址
unsigned long *hui_get_sys_call_table(void) {
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

// 初始化 Hook (把 execve 替换掉)
int hui_hook_init(void) {
    hui_state.sys_call_table = hui_get_sys_call_table();
    if (!hui_state.sys_call_table) return -EFAULT;

    hui_state.original_execve = (void *)hui_state.sys_call_table[__NR_execve];

    hui_disable_wp();
    hui_state.sys_call_table[__NR_execve] = (unsigned long)hui_hooked_execve_compat;
    hui_enable_wp();

    pr_info("HUI: execve 劫持成功！\n");
    return 0;
}

// 退出 Hook (恢复原样)
void hui_hook_exit(void) {
    if (hui_state.sys_call_table && hui_state.original_execve) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_execve] = (unsigned long)hui_state.original_execve;
        hui_enable_wp();
        pr_info("HUI: execve 已恢复原状。\n");
    }
}
