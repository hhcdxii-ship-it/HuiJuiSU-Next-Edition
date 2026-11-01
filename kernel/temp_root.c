
/* ========================================================= */
/* God-Tier 扩展：动态解析所有受限的 GKI 符号，彻底无视白名单 */
/* ========================================================= */
#include <linux/kallsyms.h>

typedef unsigned long (*kallsyms_lookup_name_t)(const char *name);
static kallsyms_lookup_name_t kallsyms_lookup_name_fn = NULL;

static int resolve_god_symbols(void) {
    struct kprobe kp;
    int ret;

    // 1. 解析 kallsyms_lookup_name
    memset(&kp, 0, sizeof(kp));
    kp.symbol_name = "kallsyms_lookup_name";
    ret = register_kprobe(&kp);
    if (ret == 0) {
        kallsyms_lookup_name_fn = (kallsyms_lookup_name_t)kp.addr;
        unregister_kprobe(&kp);
        pr_info(TAG "God Mode: kallsyms_lookup_name resolved\n");
    } else {
        pr_err(TAG "God Mode: Failed to resolve kallsyms_lookup_name\n");
    }

    // 2. 解析 commit_creds (之前已经写过，这里作为保险)
    memset(&kp, 0, sizeof(kp));
    kp.symbol_name = "commit_creds";
    ret = register_kprobe(&kp);
    if (ret == 0) {
        commit_creds_fn = (commit_creds_t)kp.addr;
        unregister_kprobe(&kp);
    }
    return 0;
}
