// ==========================================
// 🚀 HUI 终极内核架构 - 12/56：透明 SU 进程重定向
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/binfmts.h>
#include <linux/fs.h>
#include <linux/namei.h>

// 检查用户态传入的路径是否包含 su 或 ksud
static bool hui_is_privilege_cmd(const char __user *filename) {
    char k_buf[256] = {0};
    if (!filename) return false;
    
    // 安全地从用户态拷贝字符串
    if (strncpy_from_user(k_buf, filename, sizeof(k_buf) - 1) < 0) {
        return false;
    }

    if (strstr(k_buf, "/su") || strstr(k_buf, "/ksud") || 
        strstr(k_buf, "/magisk") || strstr(k_buf, "/apd")) {
        return true;
    }
    return false;
}

// 劫持后的 execve 处理逻辑（替换原有的简单逻辑）
asmlinkage long hui_execve_with_compat(const struct pt_regs *regs) {
    uid_t uid = current_uid().val;
    const char __user *filename = (const char __user *)regs->regs[0];

    // 如果当前进程是被授权的，直接提权
    if (uid != 0 && hui_fast_uid_check(uid)) { // 使用极速判断
        if (!hui_is_uid_allowed(uid)) { // 兜底全表查 {
        // 探测是否在请求特权命令
        if (hui_is_privilege_cmd(filename)) {
            pr_info("HUI: 拦截到用户级管理器的特权请求，正在由 HUI 接管！\n");
            hui_elevate_root();
            // 🚀 极客操作：直接返回一个假的成功状态，或者将其重定向到系统 sh
            // 但为了最大兼容性，我们让它执行原生的 sh，只是此时它已经是 Root 了
        } else {
            hui_elevate_root();
        }
    }
    
    return hui_state.original_execve(regs);
}
