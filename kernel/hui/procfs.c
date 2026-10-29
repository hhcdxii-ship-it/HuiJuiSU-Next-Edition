#include "hui.h"

#define HUI_PROC_NAME "hui_status"

static int hui_proc_show(struct seq_file *m, void *v) {
    seq_printf(m, "========== HUI 内核模块状态 ==========\n");
    seq_printf(m, "Version: %s\n", HUI_VERSION_CODE);
    seq_printf(m, "Status: Active (Pure-Blood)\n");
    seq_printf(m, "Authorized Apps: %u\n", hui_state.app_count);
    seq_printf(m, "Syscall Table: %px\n", hui_state.sys_call_table);
    seq_printf(m, "Execve Hooked: %s\n", hui_state.is_initialized ? "Yes" : "No");
    seq_printf(m, "Kernel UID: %d\n", current_uid().val);
    seq_printf(m, "======================================\n");
    return 0;
}

static int hui_proc_open(struct inode *inode, struct file *file) {
    return single_open(file, hui_proc_show, NULL);
}

static const struct proc_ops hui_proc_ops = {
    .proc_open = hui_proc_open,
    .proc_read = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};

// 🚀 必须是全局，去掉 unused
int hui_proc_init(void) {
    if (!proc_create(HUI_PROC_NAME, 0444, NULL, &hui_proc_ops)) return -ENOMEM;
    pr_info("HUI: /proc/%s 已创建\n", HUI_PROC_NAME);
    return 0;
}

void hui_proc_exit(void) {
    remove_proc_entry(HUI_PROC_NAME, NULL);
}
