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

#define HUI_VERSION_CODE "1.0.0"
#define HUI_MAX_PACKAGE_LEN 256
#define HUI_HASH_BITS 8

// 兼容宏
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    #define HUI_CURRENT_UID (current_uid().val)
#else
    #define HUI_CURRENT_UID (current_uid())
#endif

#if defined(__aarch64__)
    #define HUI_SYSCALL_ARG0(regs) ((regs)->regs[0])
    #define HUI_SYSCALL_ARG1(regs) ((regs)->regs[1])
    #define HUI_SYSCALL_ARG2(regs) ((regs)->regs[2])
#else
    #define HUI_SYSCALL_ARG0(regs) 0
    #define HUI_SYSCALL_ARG1(regs) 0
    #define HUI_SYSCALL_ARG2(regs) 0
#endif

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

extern struct hui_core_state hui_state;
extern struct hlist_head hui_allowlist[HUI_HASH_BITS];
extern spinlock_t hui_allowlist_lock;
extern uid_t blocked_net_uids[16];

// 核心函数声明
int hui_comm_init(void);
void hui_comm_exit(void);
int hui_proc_init(void);
void hui_proc_exit(void);
int hui_config_init(void);
void hui_config_exit(void);
bool hui_is_uid_allowed(uid_t uid);
int hui_add_uid(uid_t uid, const char *pkg_name);
void hui_destroy_allowlist(void);
int hui_hook_init(void);
void hui_hook_exit(void);
bool hui_check_other_hooks(void);
int hui_check_selinux_status(void);
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
void hui_import_external_allowlists(void);
void hui_boost_game_performance(void);
int hui_delay_init_hook(void);
void __init hui_free_init_memory(void);

#endif
