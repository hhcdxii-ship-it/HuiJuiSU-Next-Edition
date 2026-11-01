#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>

#define TAG "HuiJuiGuard: "
static struct kprobe kp_sig;

static int handler_pre_do_send_sig_info(struct kprobe *p, struct pt_regs *regs) {
    // 在 arm64 上，do_send_sig_info 的参数通常在 x0(sig), x2(task)
    int sig = (int)regs->regs[0];
    struct task_struct *task = (struct task_struct *)regs->regs[2];

    if (task && (sig == SIGTRAP || sig == SIGKILL)) {
        char comm[TASK_COMM_LEN];
        get_task_comm(comm, task);
        if (strstr(comm, "huijui") != NULL) {
            return 1; // 直接拦截吞掉
        }
    }
    return 0;
}

static int __init huijui_guard_init(void) {
    kp_sig.symbol_name = "do_send_sig_info";
    kp_sig.pre_handler = handler_pre_do_send_sig_info;
    int ret = register_kprobe(&kp_sig);
    if (ret < 0) return ret;
    pr_info(TAG "guard module loaded\n");
    return 0;
}

static void __exit huijui_guard_exit(void) {
    unregister_kprobe(&kp_sig);
}

module_init(huijui_guard_init);
module_exit(huijui_guard_exit);
MODULE_LICENSE("GPL");
