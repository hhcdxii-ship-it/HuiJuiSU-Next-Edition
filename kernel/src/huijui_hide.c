#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/fs.h>
#include <linux/dcache.h>
#include <linux/string.h>
#include <linux/version.h>

#define TAG "HuiJuiHide: "

static struct kprobe kp_filldir;
static const char HIDE_MARK[] = "huijui";

/*
 * filldir/filldir64 是「枚举目录项」时才会调用的，频率远低于 read/open，
 * 是隐藏目录的正确挂点。
 *
 * 但它是按**目录项**逐个调用的 —— 列一个 3000 项的目录就会进 3000 次。
 * 原实现每次都对每个名字跑一遍 strstr，这里加两字节前缀预判：
 * 绝大多数文件名在这里就直接返回，避免进入完整的子串搜索。
 */
static int handler_pre_filldir(struct kprobe *p, struct pt_regs *regs)
{
	const char *name = (const char *)regs->regs[1];

	if (!name)
		return 0;

	/* 快路径：首字母不是 h 就不可能命中（读取 name[1] 前已确认 name[0] 非 0） */
	if (name[0] != HIDE_MARK[0])
		return 0;
	if (name[1] != HIDE_MARK[1])
		return 0;

	if (strstr(name, HIDE_MARK) != NULL)
		return 1; /* 非 0 表示跳过该目录项 */

	return 0;
}

static int __init huijui_hide_init(void)
{
	int ret;

	kp_filldir.symbol_name = "filldir64";
	kp_filldir.pre_handler = handler_pre_filldir;
	ret = register_kprobe(&kp_filldir);
	if (ret < 0) {
		kp_filldir.symbol_name = "filldir";
		ret = register_kprobe(&kp_filldir);
		if (ret < 0) {
			pr_err(TAG "register kprobe failed: %d\n", ret);
			return ret;
		}
	}
	pr_info(TAG "hide module loaded (fast-path filter)\n");
	return 0;
}

static void __exit huijui_hide_exit(void)
{
	unregister_kprobe(&kp_filldir);
}

module_init(huijui_hide_init);
module_exit(huijui_hide_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("HuiJuiSU dirent hiding with fast-path filter");
