#include <linux/module.h>
#include <linux/kernel.h>

#define TAG "HuiJuiInit: "

/*
 * 曾经在 do_execveat_common 上挂了一个 pre_handler 全是空实现的 kprobe。
 *
 * 代价：execve 是每次进程创建都要走的热路径，kprobe 会在该地址上打断点，
 * 每次命中都要陷入异常、保存/恢复完整寄存器上下文、查 kprobe 哈希表。
 * 而处理函数里 `return 0;` 什么都不做 —— 纯开销。
 *
 * init 注入是靠 ramdisk 里的 huijuiinit 换掉 /init 实现的，跟这个钩子无关。
 * 所以这里不再注册任何断点，模块只保留一个可被 /proc/modules 看见的载体。
 */
int huijui_init_register(void)
{
	pr_info(TAG "loaded (no hot-path hooks)\n");
	return 0;
}

void huijui_init_unregister(void)
{
	pr_info(TAG "unloaded\n");
}

MODULE_DESCRIPTION("HuiJuiSU init marker (no kprobe)");

/* 由 huijui_main.c 统一 module_init，本文件只提供 register/unregister */
