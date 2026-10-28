// ==========================================
// 🚀 HUI 终极内核架构 - 4/56：主模块入口
// ==========================================
#include "hui/hui.h"

// 全局状态变量定义
struct hui_core_state hui_state;

static int __init hui_core_init(void) {
    int ret;

    pr_info("====================================\n");
    pr_info("  🚀 HUI Core %s 正在启动\n", HUI_VERSION_CODE);
    pr_info("====================================\n");

    // 1. 初始化全局状态与锁
    memset(&hui_state, 0, sizeof(hui_state));
    mutex_init(&hui_state.lock);
    hash_init(hui_allowlist);
    spin_lock_init(&hui_allowlist_lock);

    // 2. 初始化通信设备 (/dev/huijui)
    ret = hui_comm_init();
    if (ret) {
        pr_err("HUI: 通信引擎初始化失败！\n");
        return ret;
    }

    // 3. 安装 Hook 劫持 (sys_call_table)
    ret = hui_delay_init_hook();
    if (ret) {
        pr_err("HUI: 劫持引擎初始化失败，开始回滚...\n");
        hui_comm_exit();
        return ret;
    }

    // 4. 注册状态监控 (procfs)
    ret = hui_proc_init();
    if (ret) {
        pr_err("HUI: 状态监控注册失败，开始回滚...\n");
        hui_hook_exit();
        hui_comm_exit();
        return ret;
    }

    hui_check_selinux_status();
    hui_import_external_allowlists();
    hui_protect_init();

    hui_mount_guard_init();
    hui_mount_modules();

    hui_state.is_initialized = true;
    hui_free_init_memory();

    pr_info("HUI: 核心接管完毕，全系统就绪！\n");
    return 0;
}

static void __exit hui_core_exit(void) {
    pr_info("HUI: 正在卸载核心模块...\n");

    if (!hui_state.is_initialized) return;

    // 1. 恢复系统调用（优先执行，防止系统崩溃）
    hui_protect_exit();

    hui_hook_exit();

    // 2. 销毁状态监控
    hui_proc_exit();

    // 3. 销毁通信设备
    hui_comm_exit();

    // 3. 清理授权表，释放所有内存
    hui_destroy_allowlist();

    mutex_destroy(&hui_state.lock);
    pr_info("HUI: 核心模块安全退出，再见！\n");
}

late_initcall(hui_core_init);
module_exit(hui_core_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("HuiJuiSU Team");
MODULE_DESCRIPTION("HuiJuiSU Pure-Blood Kernel Module");
MODULE_VERSION(HUI_VERSION_CODE);
