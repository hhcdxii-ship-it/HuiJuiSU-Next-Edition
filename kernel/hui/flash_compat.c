#include "hui.h"
#include "hui_compat.h"
#include <linux/fs.h>
#include <linux/file.h>

static uid_t flash_authorized_uid = -1;

void hui_set_flash_token(uid_t uid) {
    flash_authorized_uid = uid;
}

asmlinkage long hui_hooked_write_flash_compat(const struct pt_regs *regs) {
    unsigned int fd = (unsigned int)HUI_SYSCALL_ARG0(regs);
    uid_t uid = HUI_CURRENT_UID;

    if (uid == flash_authorized_uid) {
        return hui_state.original_execve(regs);
    }
    return hui_state.original_execve(regs);
}

int hui_flash_compat_init(void) {
    return 0;
}
