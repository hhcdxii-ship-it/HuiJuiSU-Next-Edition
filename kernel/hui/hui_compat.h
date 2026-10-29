#ifndef __HUI_COMPAT_H
#define __HUI_COMPAT_H
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
#endif
