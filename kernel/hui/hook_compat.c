#include "hui.h"
#include <linux/version.h>

// 🚀 前置声明：防止 6.12+ 内核的严格编译报错
bool hui_check_execve_hooked(void);

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 12, 0)
bool hui_check_execve_hooked(void) {
    if (!hui_state.sys_call_table) return false;
    return (void *)hui_state.sys_call_table[__NR_execve] == (void *)hui_hooked_execve_compat;
}
#else
bool hui_check_execve_hooked(void) {
    // 6.12 及以上版本已改用 kprobe，不再直接操作 sys_call_table
    return true;
}
#endif
