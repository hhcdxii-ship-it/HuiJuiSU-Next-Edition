#include "hui.h"

static asmlinkage long (*original_connect)(const struct pt_regs *regs);
uid_t blocked_net_uids[16] = {0};

bool hui_is_net_blocked(uid_t uid) {
    int i;
    for (i = 0; i < 16; i++) {
        if (blocked_net_uids[i] == uid && uid != 0) return true;
    }
    return false;
}

asmlinkage long hui_hooked_connect(const struct pt_regs *regs) {
    uid_t uid = HUI_CURRENT_UID;
    if (hui_is_net_blocked(uid)) return -EACCES;
    return original_connect(regs);
}

int hui_net_guard_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;
    original_connect = (void *)hui_state.sys_call_table[__NR_connect];
    hui_disable_wp();
    hui_state.sys_call_table[__NR_connect] = (unsigned long)hui_hooked_connect;
    hui_enable_wp();
    return 0;
}

void hui_net_guard_exit(void) {
    if (hui_state.sys_call_table && original_connect) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_connect] = (unsigned long)original_connect;
        hui_enable_wp();
    }
}
