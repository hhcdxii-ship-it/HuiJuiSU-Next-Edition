#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/version.h>

static int huijui_pre_handler(struct kprobe *p, struct pt_regs *regs) {
    return 0;
}

static struct kprobe huijui_kp = {
    .symbol_name = "do_sys_openat2",
    .pre_handler = huijui_pre_handler,
};

static int __init huijui_lkm_init(void) {
    int ret;
    pr_info("HuiJuiSU: 纯血 LKM 开始加载...\n");
    ret = register_kprobe(&huijui_kp);
    if (ret < 0) {
        pr_err("HuiJuiSU: 注册 Kprobe 失败\n");
        return ret;
    }
    pr_info("HuiJuiSU: 纯血内核模块注入成功！\n");
    return 0;
}

static void __exit huijui_lkm_exit(void) {
    unregister_kprobe(&huijui_kp);
    pr_info("HuiJuiSU: 纯血内核模块已安全卸载。\n");
}

module_init(huijui_lkm_init);
module_exit(huijui_lkm_exit);
MODULE_LICENSE("GPL");
MODULE_AUTHOR("HuiJuiSU Team");
MODULE_DESCRIPTION("HuiJuiSU Next Edition Pure-Blood Core");
