#include <linux/module.h>
#include <linux/miscdevice.h>
#include <linux/fs.h>
#include <linux/uaccess.h>
#include <linux/slab.h>

#define DEV_NAME "huijui"
#define TAG "HuiJuiSU: "

static long huijui_ioctl(struct file *filp, unsigned int cmd, unsigned long arg) {
    pr_info(TAG "Received cmd: 0x%x\n", cmd);
    return 0;
}

static const struct file_operations huijui_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = huijui_ioctl,
};

static struct miscdevice huijui_dev = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = DEV_NAME,
    .fops = &huijui_fops,
    .mode = 0666,
};

static int __init temp_root_init(void) {
    int ret = misc_register(&huijui_dev);
    if (ret) return ret;
    pr_info(TAG "Device /dev/%s ready\n", DEV_NAME);
    return 0;
}

static void __exit temp_root_exit(void) {
    misc_deregister(&huijui_dev);
}

// 导入 VFS 命名空间（为了以后扩展提权用）
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver);

module_init(temp_root_init);
module_exit(temp_root_exit);
MODULE_LICENSE("GPL");
