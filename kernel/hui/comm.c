// ==========================================
// 🚀 HUI 终极内核架构 - 3/56：通信引擎 (Misc Device)
// ==========================================
#include "hui.h"
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/uaccess.h>

#define HUI_DEVICE_NAME "huijui"
#define HUI_CMD_BUF_SIZE 512

// 设备操作：处理用户态写入（格式: "UID:pkg_name"）
static __attribute__((unused)) ssize_t hui_comm_write(struct file *file, const char __user *buf, size_t count, loff_t *ppos) {
    char k_buf[HUI_CMD_BUF_SIZE];
    int ret = 0;
    int uid;
    char pkg[HUI_MAX_PACKAGE_LEN];
    char *delimiter;

    // 1. 权限拦截：只有已经拥有 root 权限的进程（比如我们的管理器）才能写入授权表
    if (current_uid().val != 0) {
        pr_warn("HUI: 拦截非法写入！非 Root 进程 (UID: %d) 无权操作 HUI 设备！\n", current_uid().val);
        return -EPERM;
    }
static __attribute__((unused)) 
 if(count >= HUI_CMD_BUF_SIZE) {
        return -EINVAL;
    }

    // 2. 安全拷贝
    if (copy_from_user(k_buf, buf, count)) {
        return -EFAULT;
    }
    k_buf[count] = '\0';

    // 3. 字符串解析 (防越界)
    delimiter = strchr(k_buf, ':');
static __attribute__((unused))   if(!delimiter) {
        return -EINVAL;
    }

    *delimiter = '\0';
    if (kstrtoint(k_buf, 10, &uid) != 0) {
        return -EINVAL;
    }

    strncpy(pkg, delimiter + 1, sizeof(pkg) - 1);
    pkg[strcspn(pkg, "\r\n")] = 0; // 去除换行符

    // 4. 添加到授权引擎
    ret = hui_add_uid(uid, pkg);
static __attribute__((unused))   if(ret == 0 || ret == -EEXIST) {
        return count; // 返回成功写入的字节数
    }
    return ret;
}

// 设备操作：处理用户态读取（目前仅返回 HUI 状态）
static __attribute__((unused)) ssize_t hui_comm_read(struct file *file, char __user *buf, size_t count, loff_t *ppos) {
    char k_buf[128];
    int len;

    if (*ppos > 0) return 0; // 读完一次返回 EOF

    len = snprintf(k_buf, sizeof(k_buf), "HUI Status: Active\nAuthorized Apps: %u\n", hui_state.app_count);
    if (len > count) len = count;

    if (copy_to_user(buf, k_buf, len)) {
        return -EFAULT;
    }

    *ppos += len;
    return len;
}

// 操作集合
static const struct file_operations hui_comm_fops = {
    .owner = THIS_MODULE,
    .write = hui_comm_write,
    .read = hui_comm_read,
    .llseek = noop_llseek,
};

// 杂项设备注册结构
static struct miscdevice hui_misc_device = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = HUI_DEVICE_NAME,
    .fops = &hui_comm_fops,
    .mode = 0666, // 节点全员可读，但写操作在代码层被锁死（只有root能写）
};
__attribute__((unused)) 
int hui_comm_init(void) {
    int ret = misc_register(&hui_misc_device);
static __attribute__((unused))   if(ret) {
        pr_err("HUI: 无法注册杂项设备 /dev/%s\n", HUI_DEVICE_NAME);
        return ret;
    }
    pr_info("HUI: 通信接口 /dev/%s 注册成功！\n", HUI_DEVICE_NAME);
    return 0;
}
__attribute__((unused)) 
void hui_comm_exit(void) {
    misc_deregister(&hui_misc_device);
    pr_info("HUI: 通信接口已注销。\n");
}
