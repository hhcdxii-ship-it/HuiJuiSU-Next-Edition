#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/ptrace.h>
#include <linux/sched.h>

#define TAG "HuiJuiSyscall: "

static struct kprobe kp_openat;

// 极简 Hook：只做判断，不做多余操作，降低功耗，减少被检测概率
static int handler_pre_openat(struct kprobe *p, struct pt_regs *regs) {
    struct task_struct *task = current;
    
    // 性能优化点：先做最快的进程名匹配，如果不需要隐藏，立马返回
    if (task->pid < 1000) {
        // 系统核心进程，直接放行，省 CPU 周期
        return 0;
    }

    // 如果你的目标进程名匹配到了 (比如你的管理器、守护进程)
    // 才去走 KSU 式的路径隐藏逻辑
    // if (strstr(task->comm, "huijui")) {
    //     // 做路径替换或隐藏
    // }

    return 0;
}

static int __init huijui_syscall_init(void) {
    int ret;
    kp_openat.symbol_name = "__arm64_sys_openat"; // 适配 GKI 的 sys_openat 符号
    kp_openat.pre_handler = handler_pre_openat;
    
    ret = register_kprobe(&kp_openat);
    if (ret < 0) {
        pr_err(TAG "Failed to hook openat: %d\n", ret);
        return ret;
    }
    pr_info(TAG "Syscall hook active with performance optimization\n");
    return 0;
}

static void __exit huijui_syscall_exit(void) {
    unregister_kprobe(&kp_openat);
}
module_init(huijui_syscall_init);
module_exit(huijui_syscall_exit);
MODULE_LICENSE("GPL");
