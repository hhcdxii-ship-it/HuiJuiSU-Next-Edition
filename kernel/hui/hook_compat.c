#include "hui.h"
#include <linux/kallsyms.h>

bool hui_check_other_hooks(void) {
    unsigned long original_execve_addr;
    unsigned long current_execve_addr;

    if (!hui_state.sys_call_table) return false;

    original_execve_addr = (unsigned long)kallsyms_lookup_name("__arm64_sys_execve");
    if (!original_execve_addr) {
        original_execve_addr = (unsigned long)kallsyms_lookup_name("sys_execve");
    }
    current_execve_addr = hui_state.sys_call_table[__NR_execve];

    if (current_execve_addr != original_execve_addr &&
        current_execve_addr != (unsigned long)hui_state.original_execve) {
        return true;
    }
    return false;
}
