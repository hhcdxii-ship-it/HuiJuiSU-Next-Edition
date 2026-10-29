#include "hui.h"

static ssize_t hui_comm_write(struct file *file, const char __user *buf, size_t count, loff_t *ppos) {
    char k_buf[512];
    int ret = 0, uid, block_uid, unblock_uid, i;
    char pkg[HUI_MAX_PACKAGE_LEN];
    char *delimiter;

    if (current_uid().val != 0) return -EPERM;
    if (count >= 512) return -EINVAL;
    if (copy_from_user(k_buf, buf, count)) return -EFAULT;
    k_buf[count] = '\0';

    if (strncmp(k_buf, "HUI_NET_BLOCK:", 14) == 0) {
        if (kstrtoint(k_buf + 14, 10, &block_uid) == 0) {
            for (i = 0; i < 16; i++) {
                if (blocked_net_uids[i] == 0) {
                    blocked_net_uids[i] = block_uid;
                    break;
                }
            }
        }
        return count;
    }

    if (strncmp(k_buf, "HUI_NET_UNBLOCK:", 16) == 0) {
        if (kstrtoint(k_buf + 16, 10, &unblock_uid) == 0) {
            for (i = 0; i < 16; i++) {
                if (blocked_net_uids[i] == unblock_uid) {
                    blocked_net_uids[i] = 0;
                    break;
                }
            }
        }
        return count;
    }

    delimiter = strchr(k_buf, ':');
    if (!delimiter) return -EINVAL;

    *delimiter = '\0';
    if (kstrtoint(k_buf, 10, &uid) != 0) return -EINVAL;

    strncpy(pkg, delimiter + 1, sizeof(pkg) - 1);
    pkg[strcspn(pkg, "\r\n")] = 0;

    ret = hui_add_uid(uid, pkg);
    if (ret == 0 || ret == -EEXIST) {
        return count;
    }
    return ret;
}

static ssize_t hui_comm_read(struct file *file, char __user *buf, size_t count, loff_t *ppos) {
    char k_buf[128];
    int len;

    if (*ppos > 0) return 0;
    len = snprintf(k_buf, sizeof(k_buf), "HUI Status: Active\nAuthorized Apps: %u\n", hui_state.app_count);
    if (len > count) len = count;
    if (copy_to_user(buf, k_buf, len)) return -EFAULT;
    *ppos += len;
    return len;
}

static const struct file_operations hui_comm_fops = {
    .owner = THIS_MODULE,
    .write = hui_comm_write,
    .read = hui_comm_read,
    .llseek = noop_llseek,
};

static struct miscdevice hui_misc_device = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = "huijui",
    .fops = &hui_comm_fops,
    .mode = 0666,
};

int hui_comm_init(void) {
    int ret = misc_register(&hui_misc_device);
    if (ret) return ret;
    pr_info("HUI: /dev/huijui 注册成功！\n");
    return 0;
}

void hui_comm_exit(void) {
    misc_deregister(&hui_misc_device);
}
