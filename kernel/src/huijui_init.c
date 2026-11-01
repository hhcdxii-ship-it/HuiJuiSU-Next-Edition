#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/ptrace.h>
#include <linux/sched.h>
#include <linux/fs.h>
#include <linux/string.h>

#define TAG "HuiJuiInit: "
#define HUIJUI_DAEMON_PATH "/data/adb/huijui/huijuiinit"

static struct kprobe kp_execve;

// 拦截 do_execveat_common (KSU 常用 Hook 点) 或 execve
static int handler_pre_execve(struct kprobe *p, struct pt_regs *regs) {
    // 这里的逻辑主要是监控 init 进程拉起，或者保证你的 daemon 以最高优先级启动
    // KSU 在这里会拦截并替换或者注入
    return 0;
}

static int __init huijui_init_mod_init(void) {
    int ret;
    // KSU 架构经典操作：劫持 execve 来做 init 注入
    kp_execve.symbol_name = "do_execveat_common"; 
    kp_execve.pre_handler = handler_pre_execve;
    
    ret = register_kprobe(&kp_execve);
    if (ret < 0) {
        pr_warn(TAG "do_execveat_common hook failed, trying do_execve\n");
        kp_execve.symbol_name = "do_execve";
        ret = register_kprobe(&kp_execve);
        if (ret < 0) return ret;
    }
    pr_info(TAG "Init injection & boot optimizer active\n");
    return 0;
}

static void __exit huijui_init_mod_exit(void) {
    unregister_kprobe(&kp_execve);
}
module_init(huijui_init_mod_init);
module_exit(huijui_init_mod_exit);
MODULE_LICENSE("GPL");
