#include "hui.h"

// 🚀 修复 GKI 符号未导出问题：直接使用 hui_get_sys_call_table 逻辑
// 这里不再直接调用 kallsyms_lookup_name，而是使用已知的 sys_call_table
bool hui_check_execve_hooked(void) {
    if (!hui_state.sys_call_table) return false;

    unsigned long current_execve_addr = hui_state.sys_call_table[__NR_execve];
    // 直接对比是否等于我们的 Hook 函数地址
    return (void *)current_execve_addr == (void *)hui_hooked_execve_compat;
}
