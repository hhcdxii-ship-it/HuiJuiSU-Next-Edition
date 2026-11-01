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

int huijui_mem_register(void) {
    pr_info(TAG "Memory module loaded\n");
    return 0;
}
void huijui_mem_unregister(void) {
    pr_info(TAG "Memory module unloaded\n");
}

/* 由 huijui_main.c 统一 module_init，本文件只提供 register/unregister */
