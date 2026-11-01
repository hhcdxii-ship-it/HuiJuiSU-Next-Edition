#include <linux/module.h>
#include <linux/sched.h>
#include <linux/mm.h>
#include <linux/uaccess.h>

#define TAG "HuiJuiMem: "

// 加上 __maybe_unused 防止警告
static int __maybe_unused huijui_read_other_process(int pid, unsigned long addr, void *buf, int len) {
    struct task_struct *task;
    int ret;

    rcu_read_lock();
    task = pid_task(find_vpid(pid), PIDTYPE_PID);
    if (!task) {
        rcu_read_unlock();
        return -ESRCH;
    }
    
    ret = access_process_vm(task, addr, buf, len, FOLL_FORCE);
    rcu_read_unlock();
    
    return ret;
}

static int __init huijui_mem_init(void) {
    pr_info(TAG "Memory module loaded\n");
    return 0;
}
static void __exit huijui_mem_exit(void) {
    pr_info(TAG "Memory module unloaded\n");
}
module_init(huijui_mem_init);
module_exit(huijui_mem_exit);
MODULE_LICENSE("GPL");
