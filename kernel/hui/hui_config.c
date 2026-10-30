#include "hui.h"

#define HUI_CONFIG_NAME "hui_config"

struct hui_custom_config {
    char active_governor[32];
    unsigned int max_freq;
    bool force_performance;
};
static struct hui_custom_config user_config = {
    .active_governor = "schedutil",
    .max_freq = 0,
    .force_performance = false
};

static int hui_config_show(struct seq_file *m, void *v) {
    seq_printf(m, "=== HUI 专属配置中心 ===\n");
    seq_printf(m, "CPU Governor: %s\n", user_config.active_governor);
    seq_printf(m, "Max Freq: %u\n", user_config.max_freq);
    seq_printf(m, "Performance Mode: %s\n", user_config.force_performance ? "ON" : "OFF");
    return 0;
}

static int hui_config_open(struct inode *inode, struct file *file) {
    return single_open(file, hui_config_show, NULL);
}

static ssize_t hui_config_write(struct file *file, const char __user *buf, size_t count, loff_t *ppos) {
    char k_buf[128] = {0};

    if (current_uid().val != 0) return -EPERM;
    if (count >= sizeof(k_buf)) return -EINVAL;
    if (copy_from_user(k_buf, buf, count)) return -EFAULT;

    k_buf[strcspn(k_buf, "\r\n")] = 0;

    if (strncmp(k_buf, "GOVERNOR:", 9) == 0) {
        strncpy(user_config.active_governor, k_buf + 9, 31);
    } else if (strncmp(k_buf, "PERF_MODE:", 10) == 0) {
        user_config.force_performance = (k_buf[10] == '1');
    } else {
        return -EINVAL;
    }
    return count;
}

static const struct proc_ops hui_config_ops = {
    .proc_open = hui_config_open,
    .proc_read = seq_read,
    .proc_write = hui_config_write,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};

int hui_config_init(void) {
    if (!proc_create(HUI_CONFIG_NAME, 0666, NULL, &hui_config_ops)) return -ENOMEM;
    pr_info("HuiJuiPhantom: /proc/%s 已上线！\n", HUI_CONFIG_NAME);
    return 0;
}

void hui_config_exit(void) {
    remove_proc_entry(HUI_CONFIG_NAME, NULL);
}
