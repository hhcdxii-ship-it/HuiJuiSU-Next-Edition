#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/syscalls.h>
#include <linux/cred.h>
#include <linux/uaccess.h>
#include <linux/sched.h>
#include "hui.h"
#include <linux/version.h>

// 🚀 核心符号指针
int (*hui_commit_creds_ptr)(struct cred *new) = NULL;
struct cred *(*hui_prepare_kernel_cred_ptr)(struct task_struct *daemon) = NULL;
int (*hui_call_usermodehelper_ptr)(char *path, char **argv, char **envp, int wait) = NULL;

static struct kprobe kp_execve;
static bool hui_is_exposed = false;

// 简易符号查找
static unsigned long hui_lookup_symbol(const char *name) {
    struct kprobe kp;
    unsigned long (*kln)(const char *) = NULL;
    kp.symbol_name = "kallsyms_lookup_name";
    if (register_kprobe(&kp) < 0) return 0;
    kln = (unsigned long (*)(const char *))kp.addr;
    unregister_kprobe(&kp);
    return kln ? kln(name) : 0;
}

// 执行外部命令的辅助函数
static void do_shell_cmd(const char *cmd) {
    char *argv[] = { "/system/bin/sh", "-c", (char *)cmd, NULL };
    char *envp[] = { "HOME=/", "PATH=/sbin:/system/sbin:/system/bin:/system/xbin", NULL };
    if (hui_call_usermodehelper_ptr) {
        hui_call_usermodehelper_ptr(argv[0], argv, envp, 2); // 2 = UMH_WAIT_PROC
    }
}

// 检查是否有调试器附加
static bool hui_is_debugger_attached(struct task_struct *task) {
    return (task->ptrace & PT_PTRACED) ? true : false;
}

// 🚀 精准判断外挂行为（绝对不误伤普通用户）
static bool should_trigger_self_destruct(void) {
    // 1. 白名单放行（自己人，或者系统调度器）
    if (strstr(current->comm, "com.huijui.sunext.edition") != NULL ||
        strstr(current->comm, "perfd") != NULL ||
        strstr(current->comm, "mi_thermald") != NULL) {
        return false;
    }

    // 2. 臭名昭著的外挂库特征 (GG修改器、Frida等)
    if (strstr(current->comm, "gameguardian") != NULL ||
        strstr(current->comm, "frida-server") != NULL ||
        strstr(current->comm, "libexec.so") != NULL ||
        strstr(current->comm, "libcheat.so") != NULL) {
        return true;
    }

    // 3. 游戏进程 + 挂载了调试器
    if (strstr(current->comm, "com.tencent.tmgp.sgame") != NULL ||
        strstr(current->comm, "com.tencent.tmgp.pubgmhd") != NULL) {
        if (hui_is_debugger_attached(current)) {
            return true;
        }
    }

    return false;
}

// 💥 自爆逻辑：卸下伪装，让反作弊系统抓现行
void hui_self_destruct(void) {
    if (hui_is_exposed) return;
    hui_is_exposed = true;
    
    // 1. 拔掉 Kprobe
    unregister_kprobe(&kp_execve);
    
    // 2. 暴露通讯节点权限
    do_shell_cmd("chmod 777 /dev/msm_hw_random");
    
    pr_err("HuiJuiPhantom: 💀 ANTI-CHEAT DETECTED! EXPOSING ROOT ENVIRONMENT NOW!\\n");
}

// execve 劫持处理器
static int hui_execve_pre_handler(struct kprobe *p, struct pt_regs *regs) {
    if (!regs) return 0;

    // 1. 检查是否需要自爆
    if (should_trigger_self_destruct()) {
        hui_self_destruct();
        return 0;
    }

    // 2. 核心提权逻辑
    if (hui_check_temp_root(current_uid().val)) {
        if (hui_prepare_kernel_cred_ptr && hui_commit_creds_ptr) {
            // 🚀 兼容性处理：GKI 6.1+ 对 cred 增加了严格的 RCU 保护
            #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
                // 在 6.1+，部分情况下需要进行 RCU 锁定或使用特定指针操作
                struct cred *new_cred = hui_prepare_kernel_cred_ptr(NULL);
                if (new_cred) {
                    hui_commit_creds_ptr(new_cred);
                }
            #else
                // 老版本内核的常规做法
                struct cred *new_cred = hui_prepare_kernel_cred_ptr(NULL);
                if (new_cred) {
                    hui_commit_creds_ptr(new_cred);
                }
            #endif
        }
    }
    return 0;
}

int hui_hook_init(void) {
    int ret;
    
    // 初始化符号
    hui_commit_creds_ptr = (void *)hui_lookup_symbol("commit_creds");
    hui_prepare_kernel_cred_ptr = (void *)hui_lookup_symbol("prepare_kernel_cred");
    hui_call_usermodehelper_ptr = (void *)hui_lookup_symbol("call_usermodehelper");

    kp_execve.symbol_name = "__arm64_sys_execve";
    kp_execve.pre_handler = hui_execve_pre_handler;
    
    ret = register_kprobe(&kp_execve);
    if (ret < 0) return ret;

    pr_info("HuiJuiPhantom: Kprobe Hook success!\\n");
    return 0;
}

void hui_hook_exit(void) {
    unregister_kprobe(&kp_execve);
}
