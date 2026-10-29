#include "hui/hui.h"

struct hui_core_state hui_state;

static int __init hui_core_init(void) {
    int ret;
    pr_info("HUI: init...\n");
    memset(&hui_state, 0, sizeof(hui_state));
    mutex_init(&hui_state.lock);
    hash_init(hui_allowlist);
    spin_lock_init(&hui_allowlist_lock);

    ret = hui_comm_init();
    if (ret) return ret;

    ret = hui_hook_init();
    if (ret) { hui_comm_exit(); return ret; }

    hui_state.is_initialized = true;
    return 0;
}

static void __exit hui_core_exit(void) {
    if (!hui_state.is_initialized) return;
    hui_hook_exit();
    hui_comm_exit();
    mutex_destroy(&hui_state.lock);
    pr_info("HUI: exit.\n");
}

module_init(hui_core_init);
module_exit(hui_core_exit);
MODULE_LICENSE("GPL");
