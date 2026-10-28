#ifndef __HUI_CORE_H
#define __HUI_CORE_H

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/version.h>
#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/spinlock.h>
#include <linux/mutex.h>
#include <linux/rcupdate.h>
#include <linux/rbtree.h>
#include <linux/hashtable.h>
#include <linux/kprobes.h>
#include <linux/slab.h>

#define HUI_VERSION_CODE "1.0.0"
#define HUI_MAX_PACKAGE_LEN 256
#define HIJ_HASH_BITS 8

struct hui_app_info {
    uid_t uid;
    char package_name[HUI_MAX_PACKAGE_LEN];
    struct hlist_node hash_node;
    struct rcu_head rcu;
    atomic_t ref_count;
};

struct hui_core_state {
    unsigned long *sys_call_table;
    asmlinkage long (*original_execve)(const struct pt_regs *regs);
    bool is_initialized;
    struct mutex lock;
    unsigned int app_count;
};

// 全局变量声明
extern struct hui_core_state hui_state;
extern struct hlist_head hui_allowlist[HIJ_HASH_BITS];
extern spinlock_t hui_allowlist_lock;
extern uid_t blocked_net_uids[16];

// --- 核心提权与 Hook ---
void hui_elevate_root(void);
asmlinkage long hui_execve_with_compat(const struct pt_regs *regs);
int hui_hook_init(void);
void hui_hook_exit(void);
bool hui_check_other_hooks(void);
int hui_check_selinux_status(void);

// --- 授权与监控 ---
bool hui_is_uid_allowed(uid_t uid);
bool hui_fast_uid_check(uid_t uid);
int hui_add_uid(uid_t uid, const char *pkg_name);
void hui_remove_uid(uid_t uid);
void hui_destroy_allowlist(void);
int hui_comm_init(void);
void hui_comm_exit(void);
int hui_proc_init(void);
void hui_proc_exit(void);
int hui_config_init(void);
void hui_config_exit(void);

// --- 挂载与守护 ---
void hui_mount_modules(void);
int hui_protect_init(void);
void hui_protect_exit(void);
int hui_guard_init(void);
void hui_guard_exit(void);
int hui_guard_block_init(void);
void hui_guard_block_exit(void);
int hui_mount_guard_init(void);
void hui_mount_guard_exit(void);
int hui_net_guard_init(void);
void hui_net_guard_exit(void);

// --- 兼容与优化 ---
void hui_import_external_allowlists(void);
void hui_boost_game_performance(void);
int hui_delay_init_hook(void);
void __init hui_free_init_memory(void);

// 引入兼容层头文件
#include "hui_compat.h"

#endif // __HUI_CORE_H
