#include <linux/module.h>
#include <linux/kernel.h>

#define TAG "HuiJuiSyscall: "

/*
 * 原本在 __arm64_sys_openat 上挂 kprobe，处理函数里只有一句
 * `if (task->pid < 1000) return 0;` —— 也就是「查一下 pid 然后什么都不做」。
 *
 * openat 是每打开一个文件就命中的热路径。为了一个空判断付出全系统
 * 每次 open 一次断点陷阱，是纯粹的功耗与延迟浪费。
 *
 * 路径隐藏的实际工作在 huijui_hide.c（filldir），这里不再注册断点。
 */
static int __init huijui_syscall_init(void)
{
	pr_info(TAG "loaded (openat hook removed for power)\n");
	return 0;
}

static void __exit huijui_syscall_exit(void)
{
	pr_info(TAG "unloaded\n");
}

module_init(huijui_syscall_init);
module_exit(huijui_syscall_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("HuiJuiSU syscall carrier (no hot-path kprobe)");
