// ==========================================
// 🚀 HUI 终极内核架构 - 28/56：内核级网络防火墙
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/net.h>
#include <linux/socket.h>
#include <linux/syscalls.h>

// 原始 connect 指针
static asmlinkage long (*original_connect)(const struct pt_regs *regs);

// 简易的 UID 网络黑名单（实际工程可替换为内核哈希表）
uid_t blocked_net_uids[16] = {0};

// 检查 UID 是否在黑名单
static bool hui_is_net_blocked(uid_t uid) {
    int i;
    for (i = 0; i < 16; i++) {
        if (blocked_net_uids[i] == uid && uid != 0) return true;
    }
    return false;
}

// 劫持 connect 系统调用
static asmlinkage long hui_hooked_connect(const struct pt_regs *regs) {
    uid_t uid = HUI_CURRENT_UID;

    // 如果该 UID 被禁止联网，直接拦截
    if (hui_is_net_blocked(uid)) {
        pr_warn_ratelimited("HUI: 拦截到 UID [%d] 的联网请求，隐私保护生效！\n", uid);
        return -EACCES; // 拒绝访问
    }

    return original_connect(regs);
}

int hui_net_guard_init(void) {
    if (!hui_state.sys_call_table) return -EFAULT;

    original_connect = (void *)hui_state.sys_call_table[__NR_connect];

    hui_disable_wp();
    hui_state.sys_call_table[__NR_connect] = (unsigned long)hui_hooked_connect;
    hui_enable_wp();

    pr_info("HUI: 网络隐私防火墙已启动！\n");
    return 0;
}

void hui_net_guard_exit(void) {
    if (hui_state.sys_call_table && original_connect) {
        hui_disable_wp();
        hui_state.sys_call_table[__NR_connect] = (unsigned long)original_connect;
        hui_enable_wp();
        pr_info("HUI: 网络隐私防火墙已关闭。\n");
    }
}
