// ==========================================
// 🚀 HUI 终极内核架构 - 6/56：Procfs 状态监控
// ==========================================
#include "hui.h"
#include <linux/proc_fs.h>
#include <linux/seq_file.h>

#define HUI_PROC_NAME "hui_status"

// 当用户读取 /proc/hui_status 时，触发展示
static int hui_proc_show(struct seq_file *m, void *v) {
    seq_printf(m, "========== HUI 内核模块状态 ==========\n");
    seq_printf(m, "Version: %s\n", HUI_VERSION_CODE);
    seq_printf(m, "Status: Active (Pure-Blood)\n");
    seq_printf(m, "Authorized Apps: %u\n", hui_state.app_count);
    seq_printf(m, "Syscall Table: %px\n", hui_state.sys_call_table);
    seq_printf(m, "Execve Hooked: %s\n", hui_state.is_initialized ? "Yes" : "No");
    
    // 如果存在安全上下文，展示 SELinux 状态
    seq_printf(m, "Kernel UID: %d\n", current_uid().val);
    seq_printf(m, "======================================\n");
    return 0;
}

// 打开文件时的回调
static int hui_proc_open(struct inode *inode, struct file *file) {
    return single_open(file, hui_proc_show, NULL);
}

// 只读操作集
static const struct proc_ops hui_proc_ops = {
    .proc_open = hui_proc_open,
    .proc_read = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};

int hui_proc_init(void) {
    if (!proc_create(HUI_PROC_NAME, 0444, NULL, &hui_proc_ops)) {
        pr_err("HUI: 无法创建 /proc/%s 节点\n", HUI_PROC_NAME);
        return -ENOMEM;
    }
    pr_info("HUI: 状态监控节点 /proc/%s 已创建\n", HUI_PROC_NAME);
    return 0;
}

void hui_proc_exit(void) {
    remove_proc_entry(HUI_PROC_NAME, NULL);
    pr_info("HUI: 状态监控节点已移除\n");
}
