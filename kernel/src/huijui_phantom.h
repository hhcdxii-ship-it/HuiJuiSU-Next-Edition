/* SPDX-License-Identifier: GPL-2.0 */
/*
 * HUI 引擎各子模块的注册入口。
 *
 * 为什么要拆成 register/unregister：
 *   这些 .c 以前各自带 module_init()，于是 `kernel/src` 会构建出 8~9 个独立 .ko。
 *   但安装链路只认**一个** huijui_phantom.ko（huijuiinit.c 里 exec 的是
 *   `/force_insmod /huijui_phantom.ko`），assets 里也是 HuiJuiPhantom_*.ko 单文件。
 *   源码布局和交付物对不上，导致「本地能编出一堆 .ko，装进去却什么都没有」。
 *
 *   现在全部汇入 huijui_main.c 一个模块，产物名正好是 huijui_phantom.ko。
 */
#ifndef HUIJUI_PHANTOM_H
#define HUIJUI_PHANTOM_H

int huijui_proc_register(void);
void huijui_proc_unregister(void);
int huijui_init_register(void);
void huijui_init_unregister(void);
int huijui_hide_register(void);
void huijui_hide_unregister(void);
int huijui_guard_register(void);
void huijui_guard_unregister(void);
int huijui_hook_register(void);
void huijui_hook_unregister(void);
int huijui_syscall_register(void);
void huijui_syscall_unregister(void);
int huijui_mem_register(void);
void huijui_mem_unregister(void);
int huijui_selinux_register(void);
void huijui_selinux_unregister(void);

#endif /* HUIJUI_PHANTOM_H */
