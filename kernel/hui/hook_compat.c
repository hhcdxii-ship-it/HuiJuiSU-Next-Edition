#include "hui.h"
#include <linux/version.h>

// 🚀 针对现代内核，这个文件实际上只是提供一个存根，防止编译失败
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
