// ==========================================
// 🚀 HUI 终极内核架构 - Boot 分区写保护
// ==========================================
#include "hui.h"

asmlinkage long (*original_write)(const struct pt_regs *regs);

// 检查文件描述符是否指向敏感块设备
static bool hui_is_protected_device(int fd) {
    struct file *file;
    struct inode *inode;
    char *path;
    char buf[256] = {0};
    bool is_protected = false;

    file = fget(fd);
    if (!file) return false;

    inode = file_inode(file);
    if (inode && S_ISBLK(inode->i_mode)) {
        path = d_path(&file->f_path, buf, sizeof(buf));
        if (!IS_ERR(path)) {
            if (strstr(path, "boot") || strstr(path, "system") || 
                strstr(path, "vendor") || strstr(path, "init_boot")) {
                pr_warn("HuiJuiPhantom: 拦截到对受保护分区 [%s] 的写入！\n", path);
                is_protected = true;
            }
        }
    }

    fput(file);
    return is_protected;
}

static asmlinkage long hui_hooked_write(const struct pt_regs *regs) {
    unsigned int fd = (unsigned int)HUI_SYSCALL_ARG0(regs);
    
    // 如果写入的是受保护分区，直接拒绝
    if (hui_is_protected_device(fd)) {
        return -EPERM;
    }
    
    return original_write(regs);
}

int hui_guard_block_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;

    original_write = (void *)hui_state.sys_call_table[__NR_write];

    hui_disable_wp();
    hui_state.sys_call_table[__NR_write] = (unsigned long)hui_hooked_write;
    hui_enable_wp();

    pr_info("HuiJuiPhantom: Boot 分区物理级写保护已启动！\n");
    return 0;
}

void hui_guard_block_exit(void) {
    if (hui_state.sys_call_table && original_write) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_write] = (unsigned long)original_write;
        hui_enable_wp();
        pr_info("HuiJuiPhantom: Boot 分区物理级写保护已解除。\n");
    }
}
