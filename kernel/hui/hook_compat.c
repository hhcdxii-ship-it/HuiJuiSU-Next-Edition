// ==========================================
// 🚀 HUI 终极内核架构 - 9/56：Hook 共存兼容层
// ==========================================
#include "hui.h"
#include <linux/kallsyms.h>

// 检查系统调用表是否已经被其他管理器接管
bool hui_check_other_hooks(void) {
    unsigned long original_execve_addr;
    unsigned long current_execve_addr;

    if (!hui_state.sys_call_table) return false;

    // 获取内核原生 execve 的地址
    original_execve_addr = (unsigned long)kallsyms_lookup_name("__arm64_sys_execve");
    if (!original_execve_addr) {
        // 某些内核版本可能没导出，改用 __x64_sys_execve 或 sys_execve
        original_execve_addr = (unsigned long)kallsyms_lookup_name("sys_execve");
    }

    current_execve_addr = hui_state.sys_call_table[__NR_execve];

    // 如果当前表里的地址不是原生的，也不是咱们的，说明被别的管理器（如 KSU）占坑了！
    if (current_execve_addr != original_execve_addr && 
        current_execve_addr != (unsigned long)hui_state.original_execve) {
        pr_warn("HUI: 检测到其他管理器的 Hook 存在！将在链式流程中兼容。\n");
        return true;
    }
    return false;
}
