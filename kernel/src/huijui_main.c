/* SPDX-License-Identifier: GPL-2.0 */
/*
 * HUI Phantom 主模块。
 *
 * 唯一持有 module_init / module_exit 的地方。子模块按依赖顺序注册，
 * 任何一步失败都回滚已注册的部分，绝不留半初始化状态（那种状态最容易被
 * 误判成「模块加载成功但引擎不工作」）。
 */
#include <linux/module.h>
#include <linux/kernel.h>
#include "huijui_phantom.h"

#define TAG "HuiJuiPhantom: "

static int __init huijui_phantom_init(void)
{
	int ret;

	ret = huijui_proc_register();
	if (ret) {
		pr_err(TAG "proc init failed: %d\n", ret);
		return ret;
	}

	ret = huijui_init_register();
	if (ret)
		goto err_init;

	ret = huijui_hide_register();
	if (ret)
		goto err_hide;

	ret = huijui_guard_register();
	if (ret)
		goto err_guard;

	ret = huijui_hook_register();
	if (ret)
		goto err_hook;

	ret = huijui_syscall_register();
	if (ret)
		goto err_syscall;

	ret = huijui_mem_register();
	if (ret)
		goto err_mem;

	ret = huijui_selinux_register();
	if (ret)
		goto err_selinux;

	pr_info(TAG "loaded (single module, %d sub-registrars)\n", 8);
	return 0;

err_selinux:
	huijui_mem_unregister();
err_mem:
	huijui_syscall_unregister();
err_syscall:
	huijui_hook_unregister();
err_hook:
	huijui_guard_unregister();
err_guard:
	huijui_hide_unregister();
err_hide:
	huijui_init_unregister();
err_init:
	huijui_proc_unregister();
	return ret;
}

static void __exit huijui_phantom_exit(void)
{
	huijui_selinux_unregister();
	huijui_mem_unregister();
	huijui_syscall_unregister();
	huijui_hook_unregister();
	huijui_guard_unregister();
	huijui_hide_unregister();
	huijui_init_unregister();
	huijui_proc_unregister();
	pr_info(TAG "unloaded\n");
}

module_init(huijui_phantom_init);
module_exit(huijui_phantom_exit);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("HuiJuiSU Phantom engine (single module)");
