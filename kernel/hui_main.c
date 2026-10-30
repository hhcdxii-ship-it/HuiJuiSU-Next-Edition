#include "hui/hui.h"
#include <linux/list.h>
#include <linux/module.h>
#include <linux/kobject.h>
#include <linux/kallsyms.h>

struct hui_core_state hui_state;

static int __init hui_core_init(void) {
    int ret;

    pr_info("HuiJuiPhantom: Pure-Blood Kernel Module initializing...\n");

    // 1. 安全初始化锁和哈希表
    mutex_init(&hui_state.lock);
    hash_init(hui_allowlist);
    spin_lock_init(&hui_allowlist_lock);

    // 2. 注册通信设备（伪装成 msm_hw_random）
    ret = hui_comm_init();
    if (ret) {
        pr_err("HuiJuiPhantom: Comm init failed!\n");
        return ret;
    }

    // 3. Procfs 配置中心
    ret = hui_proc_init();
    if (ret) {
        pr_err("HuiJuiPhantom: Procfs init failed!\n");
        goto err_proc;
    }

    ret = hui_config_init();
    if (ret) {
        pr_err("HuiJuiPhantom: Config init failed!\n");
        goto err_config;
    }

    // 4. 安装执行劫持 (安全版)
    ret = hui_hook_init();
    if (ret) {
        pr_err("HuiJuiPhantom: Hook init failed!\n");
        goto err_hook;
    }

    // 5. 其他防御模块
    hui_protect_init();
    hui_guard_init();
    hui_guard_block_init();
    hui_mount_guard_init();
    hui_net_guard_init();
    
    // 临时Root授权引擎
    hui_temp_grant_init();
    hui_protect_guard_init();

    // 6. 导入历史授权
    hui_import_external_allowlists();
    hui_mount_modules();

    // 🚀 7. 极致隐身术
    if (!list_empty(&THIS_MODULE->list)) {
        list_del_init(&THIS_MODULE->list);
    }
    if (THIS_MODULE->mkobj.kobj.state_initialized) {
        kobject_del(&THIS_MODULE->mkobj.kobj);
    }

    hui_deep_stealth(THIS_MODULE);
    pr_info("HuiJuiPhantom: Kernel Module loaded successfully!\n");
    return 0;

err_hook:
    hui_config_exit();
err_config:
    hui_proc_exit();
err_proc:
    hui_comm_exit();
    return ret;
}

static void __exit hui_core_exit(void) {
    hui_temp_grant_exit();
    hui_protect_guard_exit();
    hui_net_guard_exit();
    hui_mount_guard_exit();
    hui_guard_block_exit();
    hui_guard_exit();
    hui_protect_exit();
    hui_hook_exit();
    hui_config_exit();
    hui_proc_exit();
    hui_comm_exit();
    pr_info("HuiJuiPhantom: Kernel Module exited.\n");
}

module_init(hui_core_init);
module_exit(hui_core_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("HuiJuiSU Team");
MODULE_DESCRIPTION("HuiJuiSU Kernel Module");
