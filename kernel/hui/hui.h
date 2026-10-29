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
#define HUI_HASH_BITS 8

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
extern struct hlist_head hui_allowlist[HUI_HASH_BITS];
extern spinlock_t hui_allowlist_lock;
extern uid_t blocked_net_uids[16];

// --- 核心提权与 Hook ---
void hui_elevate_root(void);
asmlinkage long hui_execve_with_compat(const struct pt_regs *regs);
asmlinkage long hui_hooked_execve_compat(const struct pt_regs *regs);
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

// ==========================================
// 终极补充声明（防止内核 -Wmissing-prototypes 报错）
// ==========================================
struct hui_app_info *hui_find_app_locked(uid_t uid);
void hui_free_app_info(struct rcu_head *rcu);
int hui_proc_open(struct inode *inode, struct file *file);
void hui_opt_log_execve(const char *comm);
unsigned long *hui_get_sys_call_table(void);
int hui_config_show(struct seq_file *m, void *v);
int hui_config_open(struct inode *inode, struct file *file);
asmlinkage long hui_hooked_delete_module(const struct pt_regs *regs);
void hui_set_flash_token(uid_t uid);
asmlinkage long hui_hooked_write_flash_compat(const struct pt_regs *regs);
int hui_flash_compat_init(void);
asmlinkage long hui_hooked_umount2(const struct pt_regs *regs);
asmlinkage long hui_hooked_write(const struct pt_regs *regs);
bool hui_is_protected_device(int fd);
void __init hui_free_init_memory(void);
void hui_selinux_log_avc(void);
void hui_clean_game_env(void);
bool hui_is_game_process(const char *comm);
asmlinkage long hui_hooked_unlinkat(const struct pt_regs *regs);
asmlinkage long hui_hooked_reboot(const struct pt_regs *regs);
int hui_read_file(const char *path, char *buf, size_t size);
void hui_inject_env(struct linux_binprm *bprm);
bool hui_is_allowed_debugger(void);
bool hui_is_net_blocked(uid_t uid);
asmlinkage long hui_hooked_connect(const struct pt_regs *regs);
int hui_run_mount_cmd(const char *lower, const char *upper, const char *work, const char *target);
