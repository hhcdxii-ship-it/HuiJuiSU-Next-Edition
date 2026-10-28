// ==========================================
// 🚀 HUI 终极内核架构 - 12/56：透明 SU 进程重定向
// ==========================================
#include "hui.h"
#include "hui_compat.h"
#include <linux/binfmts.h>
#include <linux/fs.h>
#include <linux/namei.h>

asmlinkage long hui_execve_with_compat(const struct pt_regs *regs) {
    uid_t uid = HUI_CURRENT_UID;
    const char __user *filename = (const char __user *)HUI_SYSCALL_ARG0(regs);

    if (uid != 0 && hui_fast_uid_check(uid)) {
        // 合法游戏/App 的提权请求
        hui_elevate_root();
    }
    
    return hui_state.original_execve(regs);
}
