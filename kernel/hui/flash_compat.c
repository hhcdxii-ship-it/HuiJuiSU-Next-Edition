#include "hui.h"

static uid_t flash_authorized_uid = -1;

static void __attribute__((unused)) hui_set_flash_token(uid_t uid) {
    flash_authorized_uid = uid;
}

static asmlinkage long __attribute__((unused)) hui_hooked_write_flash_compat(const struct pt_regs *regs) {
    return -EPERM;
}

static int __attribute__((unused)) hui_flash_compat_init(void) {
    return 0;
}
