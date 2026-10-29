// ==========================================
// 🚀 HUI 终极内核架构 - 37/56：独有内核配置引擎
// ==========================================
#include "hui.h"
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/uaccess.h>
#include <linux/cpufreq.h>

#define HUI_CONFIG_NAME "hui_config"

// 用户的独有配置数据
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

// 读取配置
int hui_config_show(struct seq_file *m, void *v) {
    seq_printf(m, "=== HUI 专属配置中心 ===\n");
    seq_printf(m, "CPU Governor: %s\n", user_config.active_governor);
    seq_printf(m, "Max Freq: %u\n", user_config.max_freq);
    seq_printf(m, "Performance Mode: %s\n", user_config.force_performance ? "ON" : "OFF");
    return 0;
}

int hui_config_open(struct inode *inode, struct file *file) {
    return single_open(file, hui_config_show, NULL);
}

// 写入配置（触发内核级调优）
static __attribute__((unused)) ssize_t hui_config_write(struct file *file, const char __user *buf, size_t count, loff_t *ppos) {
    char k_buf[128] = {0};
    
    if (current_uid().val != 0) return -EPERM; // 仅 Root 可写
    if (count >= sizeof(k_buf)) return -EINVAL;
    if (copy_from_user(k_buf, buf, count)) return -EFAULT;

    k_buf[strcspn(k_buf, "\r\n")] = 0;

    if (strncmp(k_buf, "GOVERNOR:", 9) == 0) {
        strncpy(user_config.active_governor, k_buf + 9, 31);
        pr_info("HUI: 切换 CPU 调度策略为 %s\n", user_config.active_governor);
        // 🚀 这里真实环境中会调用 cpufreq 驱动 API 立即生效
    } else if (strncmp(k_buf, "PERF_MODE:", 10) == 0) {
        user_config.force_performance = (k_buf[10] == '1');
        pr_info("HUI: 强制性能模式 %s\n", user_config.force_performance ? "开启" : "关闭");
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
static __attribute__((unused)) 
int hui_config_init(void) {
    proc_create(HUI_CONFIG_NAME, 0666, NULL, &hui_config_ops);
    pr_info("HUI: 独有配置中心 /proc/%s 已上线！\n", HUI_CONFIG_NAME);
    return 0;
}
static __attribute__((unused)) 
void hui_config_exit(void) {
    remove_proc_entry(HUI_CONFIG_NAME, NULL);
}

// 日志级别控制（预留）
static int hui_log_level = 1; // 0: 静默, 1: 普通, 2: 调试
