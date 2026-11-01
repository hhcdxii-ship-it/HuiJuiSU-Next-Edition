#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/ptrace.h>
#include <linux/fs.h>
#include <linux/dcache.h>
#include <linux/spinlock.h>

#define TAG "HuiJuiHook: "

static struct kprobe kp_vfs_read;
static DEFINE_SPINLOCK(hook_lock);
static bool in_hook = false; // 防止重入导致的死循环

// 你想隐藏的文件路径前缀，比如你的私有目录
static const char *hide_prefix = "/data/adb/huijui";

static int handler_pre_vfs_read(struct kprobe *p, struct pt_regs *regs) {
    struct file *file = (struct file *)regs->regs[0];
    char path[256];
    char *p_path;

    if (in_hook) return 0; // 防止递归拦截自己

    spin_lock(&hook_lock);
    in_hook = true;
    spin_unlock(&hook_lock);

    if (file && file->f_path.dentry) {
        p_path = d_path(&file->f_path, path, sizeof(path));
        if (!IS_ERR(p_path)) {
            // 如果发现是在读取你的隐藏文件，直接把用户态 buffer 搞成空白，或者返回错误
            if (strstr(p_path, hide_prefix) != NULL) {
                pr_info(TAG "Blocked read on %s\n", p_path);
                // 修改寄存器，直接让 vfs_read 返回 -EACCES，或者伪造数据
                regs->regs[3] = 0; // 清空 buffer 或伪造，具体看你的劫持策略
            }
        }
    }

    spin_lock(&hook_lock);
    in_hook = false;
    spin_unlock(&hook_lock);
    return 0;
}

static int __init huijui_hook_init(void) {
    int ret;
    kp_vfs_read.symbol_name = "vfs_read";
    kp_vfs_read.pre_handler = handler_pre_vfs_read;
    ret = register_kprobe(&kp_vfs_read);
    if (ret < 0) {
        pr_err(TAG "register kprobe failed: %d\n", ret);
        return ret;
    }
    pr_info(TAG "vfs_read hooked successfully\n");
    return 0;
}

static void __exit huijui_hook_exit(void) {
    unregister_kprobe(&kp_vfs_read);
}
module_init(huijui_hook_init);
module_exit(huijui_hook_exit);
MODULE_LICENSE("GPL");
