#include "hui/hui.h"

// 全局状态变量
struct hui_core_state hui_state;

static int __init hui_core_init(void) {
    int ret;

    pr_info("HUI: 纯血内核模块开始初始化...\n");

    // 1. 初始化全局状态与锁
    memset(&hui_state, 0, sizeof(hui_state));
    mutex_init(&hui_state.lock);
    hash_init(hui_allowlist);
    spin_lock_init(&hui_allowlist_lock);

    // 2. 注册通信设备 (/dev/huijui)
    ret = hui_comm_init();
    if (ret) {
        pr_err("HUI: 通信接口初始化失败！\n");
        return ret;
    }

    // 3. 注册 Procfs 状态监控与独有配置中心
    ret = hui_proc_init();
    if (ret) {
        pr_err("HUI: Procfs 初始化失败！\n");
        hui_comm_exit();
        return ret;
    }

    ret = hui_config_init();
    if (ret) {
        pr_err("HUI: 配置中心初始化失败！\n");
        hui_proc_exit();
        hui_comm_exit();
        return ret;
    }

    // 4. 安装 execve 劫持 (核心核心)
    ret = hui_hook_init();
    if (ret) {
        pr_err("HUI: Hook 初始化失败！\n");
        hui_config_exit();
        hui_proc_exit();
        hui_comm_exit();
        return ret;
    }

    // 5. 安装防御模块 (防格机、防卸载、防写分区、防联网)
    hui_protect_init();
    hui_guard_init();
    hui_guard_block_init();
    hui_mount_guard_init();
    hui_net_guard_init();

    // 6. 导入历史授权并尝试挂载模块
    hui_import_external_allowlists();
    hui_mount_modules();

    hui_state.is_initialized = true;
    pr_info("HUI: 全部初始化完成，内核接管成功！\n");
    return 0;
}

static void __exit hui_core_exit(void) {
    if (!hui_state.is_initialized) return;

    pr_info("HUI: 开始卸载内核模块...\n");

    // 1. 恢复系统调用
    hui_hook_exit();

    // 2. 卸载防御模块
    hui_net_guard_exit();
    hui_mount_guard_exit();
    hui_guard_block_exit();
    hui_guard_exit();
    hui_protect_exit();

    // 3. 注销配置和通信设备
    hui_config_exit();
    hui_proc_exit();
    hui_comm_exit();

    // 4. 清理授权表
    hui_destroy_allowlist();

    mutex_destroy(&hui_state.lock);
    pr_info("HUI: 卸载完成，江湖再见。\n");
}

module_init(hui_core_init);
module_exit(hui_core_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("HuiJuiSU Team");
MODULE_DESCRIPTION("HuiJuiSU Pure-Blood Kernel Module");
