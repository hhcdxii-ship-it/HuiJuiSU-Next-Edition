#include <linux/module.h>
#include <linux/kernel.h>

#define TAG "HuiJuiHook: "

/*
 * ⚠️ 这里原本在 vfs_read 上注册了一个 kprobe，是全项目最大的功耗来源：
 *
 *   1. vfs_read 是全系统最热的函数之一 —— 每一次文件读取、每一次 /proc 读取、
 *      每一次包管理扫描都会命中。kprobe 命中 = 断点异常 + 完整寄存器保存/恢复
 *      + kprobe 哈希查找 + 重入保护，单次开销在微秒量级，频率却是「每秒成千上万次」。
 *
 *   2. 处理函数里那句 `regs->regs[3] = 0;` 其实**什么也没做**：
 *      arm64 上 x3 是 vfs_read 的第 4 个参数（loff_t *pos），改它既拦不住读取，
 *      也不会影响返回值。真正要拦需要改 X0 返回值并跳过原函数，代价更高。
 *
 *   3. 隐藏 /data/adb/huijui 这件事已经由 huijui_hide.c 的 filldir 钩子完成，
 *      而 filldir 的调用频率比 vfs_read 低两个数量级。
 *
 * 所以这里不再注册任何断点。目录隐藏统一走 huijui_hide。
 */
int huijui_hook_register(void)
{
	pr_info(TAG "loaded (vfs_read hook removed for power)\n");
	return 0;
}

void huijui_hook_unregister(void)
{
	pr_info(TAG "unloaded\n");
}

MODULE_DESCRIPTION("HuiJuiSU hook carrier (no hot-path kprobe)");

/* 由 huijui_main.c 统一 module_init，本文件只提供 register/unregister */
