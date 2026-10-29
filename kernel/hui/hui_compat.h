#ifndef __HUI_COMPAT_H
#define __HUI_COMPAT_H

#include <linux/version.h>
#include <linux/sched.h>
#include <linux/cred.h>

// UID 获取宏
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    #define HUI_CURRENT_UID (current_uid().val)
#else
    #define HUI_CURRENT_UID (current_uid())
#endif

// 系统调用参数获取宏
#if defined(__aarch64__)
    #define HUI_SYSCALL_ARG0(regs) ((regs)->regs[0])
    #define HUI_SYSCALL_ARG1(regs) ((regs)->regs[1])
    #define HUI_SYSCALL_ARG2(regs) ((regs)->regs[2])
#else
    #define HUI_SYSCALL_ARG0(regs) 0
    #define HUI_SYSCALL_ARG1(regs) 0
    #define HUI_SYSCALL_ARG2(regs) 0
#endif

// 写保护操作（汇编级）
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

#endif // __HUI_COMPAT_H
