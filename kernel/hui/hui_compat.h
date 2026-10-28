// ==========================================
// 🚀 HUI 终极内核架构 - 22/56：内核 API 版本兼容层
// ==========================================
#ifndef __HUI_COMPAT_H
#define __HUI_COMPAT_H

#include <linux/version.h>
#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/kallsyms.h>

// 1. 兼容 current_uid() 在不同内核中的不同返回类型
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    // 新版内核通常需要 .val 或者直接强转
    #define HUI_CURRENT_UID (current_uid().val)
#else
    #define HUI_CURRENT_UID (current_uid())
#endif

// 2. 兼容 kallsyms_lookup_name 在 5.7+ 被隐藏的问题
// 如果系统没有导出，咱们需要改用 kprobe，如果导出了直接用
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 7, 0)
    // 在这里声明，实际获取我们在 hook.c 里用 kprobe 搞
    extern unsigned long *hui_get_sys_call_table_kprobe(void);
#else
    #define hui_get_sys_call_table_kprobe() ((unsigned long *)kallsyms_lookup_name("sys_call_table"))
#endif

// 3. 兼容 ARM64 和 x86_64 架构的寄存器读取
#if defined(__aarch64__)
    // ARM64 架构的 pt_regs 寄存器读取
    #define HUI_SYSCALL_ARG0(regs) ((regs)->regs[0])
    #define HUI_SYSCALL_ARG1(regs) ((regs)->regs[1])
#elif defined(__x86_64__)
    // x86_64 架构的 pt_regs 寄存器读取
    #define HUI_SYSCALL_ARG0(regs) ((regs)->di)
    #define HUI_SYSCALL_ARG1(regs) ((regs)->si)
#else
    #error "HUI 暂不支持当前架构！"
#endif

// 4. 兼容某些老版本不支持的宏
#ifndef DEFINE_HASHTABLE
    #define DEFINE_HASHTABLE(name, bits) struct hlist_head name[1 << (bits)]
#endif

#endif // __HUI_COMPAT_H
