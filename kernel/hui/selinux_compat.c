// ==========================================
// 🚀 HUI 终极内核架构 - 10/56：SELinux 兼容与检查
// ==========================================
#include "hui.h"

// 检查当前系统的 SELinux 是否处于 Enforcing 模式
int hui_check_selinux_status(void) {
    int ret;
    struct file *fp;
    // 通过读取内核里的 selinux_state 变量（如果可用）
    // 或者简单点，直接读取 /sys/fs/selinux/enforce 节点的值
    char buf[16] = {0};
    loff_t pos = 0;

    fp = filp_open("/sys/fs/selinux/enforce", O_RDONLY, 0);
    if (IS_ERR(fp)) {
        pr_info("HUI: SELinux 节点不存在，可能是 Permissive 或已关闭。\n");
        return 0; // 非强制模式
    }

    ret = kernel_read(fp, buf, sizeof(buf) - 1, &pos);
    filp_close(fp, NULL);

    if (ret > 0 && buf[0] == '1') {
        pr_info("HUI: SELinux 处于 Enforcing 强制模式。\n");
        return 1;
    }
    return 0;
}

// 针对性的提权逻辑（如果 SELinux 报错，需要更底层的绕过，这里仅作为监控）
static __attribute__((unused)) void hui_selinux_log_avc(void) {
    // 此处预留：当 avc denied 发生时，可以记录审计日志
    // 真实的 bypass 往往需要在 sepolicy 中打补丁，或者在内核里拦截 avc_has_perm
}
