#include <linux/module.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>

#define TAG "HuiJuiProc: "
#define PROC_NAME "huijui_state"

static int huijui_proc_show(struct seq_file *m, void *v) {
    seq_printf(m, "HuiJuiSU: Active\n");
    return 0;
}

static int huijui_proc_open(struct inode *inode, struct file *file) {
    return single_open(file, huijui_proc_show, NULL);
}

static const struct proc_ops huijui_proc_ops = {
    .proc_open = huijui_proc_open,
    .proc_read = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};

int huijui_proc_register(void) {
    proc_create(PROC_NAME, 0444, NULL, &huijui_proc_ops);
    return 0;
}

void huijui_proc_unregister(void) {
    remove_proc_entry(PROC_NAME, NULL);
}

/* 由 huijui_main.c 统一 module_init，本文件只提供 register/unregister */
