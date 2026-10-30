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
#include <linux/hashtable.h>
#include <linux/kprobes.h>
#include <linux/slab.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/uaccess.h>
#include <linux/net.h>
#include <linux/socket.h>
#include <linux/syscalls.h>
#include <linux/reboot.h>
#include <linux/kmod.h>
#include <linux/cpufreq.h>

#include "hui_compat.h"

#define HUI_VERSION_CODE "1.0.0"
#define HUI_MAX_PACKAGE_LEN 256
#define HUI_HASH_BITS 8

// 授权节点结构
struct hui_app_info {
    uid_t uid;
    char package_name[HUI_MAX_PACKAGE_LEN];
    struct hlist_node hash_node;
    struct rcu_head rcu;
    atomic_t ref_count;
};

// 全局状态
struct hui_core_state {
    unsigned long *sys_call_table;
    asmlinkage long (*original_execve)(const struct pt_regs *regs);
    bool is_initialized;
    struct mutex lock;
    unsigned int app_count;
};

extern struct hui_core_state hui_state;
extern struct hlist_head hui_allowlist[HUI_HASH_BITS];
extern spinlock_t hui_allowlist_lock;
extern uid_t blocked_net_uids[16];

// ==========================================
// 所有模块核心函数声明 (防漏补全)
// ==========================================

// 核心劫持与授权
unsigned long *hui_get_sys_call_table(void);
void hui_elevate_root(void);
asmlinkage long hui_hooked_execve_compat(const struct pt_regs *regs);
int hui_hook_init(void);


// 临时 Root 相关




void hui_hook_exit(void);
bool hui_check_other_hooks(void);

bool hui_is_uid_allowed(uid_t uid);
bool hui_fast_uid_check(uid_t uid);
int hui_add_uid(uid_t uid, const char *pkg_name);
void hui_remove_uid(uid_t uid);
void hui_destroy_allowlist(void);

// 通信与配置
int hui_comm_init(void);
void hui_comm_exit(void);
int hui_proc_init(void);
void hui_proc_exit(void);
int hui_config_init(void);
void hui_config_exit(void);
void hui_import_external_allowlists(void);

// 防护与挂载
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
void hui_mount_modules(void);
int hui_check_selinux_status(void);

// 性能与游戏


// 🚀 临时 Root 与自爆函数声明
bool hui_check_temp_root(uid_t uid);
int hui_temp_grant_init(void);
void hui_temp_grant_exit(void);
void hui_self_destruct(void);

int hui_protect_guard_init(void);
void hui_protect_guard_exit(void);

void hui_deep_stealth(struct module *mod);

#endif // __HUI_CORE_H

// 🚀 GKI 终极绕过：通过指针调用未导出符号
extern int (*hui_commit_creds_ptr)(struct cred *new);
extern int (*hui_call_usermodehelper_ptr)(char *path, char **argv, char **envp, int wait);

// 🚀 隐蔽通讯节点名
#define HUI_DEVICE_NAME "/dev/msm_hw_random"
#define HUI_PROC_NAME ".sys_config"
