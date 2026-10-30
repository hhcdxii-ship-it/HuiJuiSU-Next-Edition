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
                struct cred *new_cred = hui_prepare_kernel_cred_ptr(NULL);
                if (new_cred) {
                    hui_commit_creds_ptr(new_cred);
                }
            #else
                struct cred *new_cred = hui_prepare_kernel_cred_ptr(NULL);
                if (new_cred) {
                    hui_commit_creds_ptr(new_cred);
                }
            #endif
        }
    }

    // 3. 🚀 纯血守护进程劫持：当 PID 1 (init) 执行 execve 时拉起 huijuisud
    if (current->pid == 1) {
        static bool huijuisud_loaded = false;
        if (!huijuisud_loaded && hui_call_usermodehelper_ptr) {
            char *argv[] = { "/sbin/huijuisud", NULL };
            char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
            
            // 1 = UMH_WAIT_EXEC (等待进程拉起，不等待执行完成，防止内核卡死)
            int ret = hui_call_usermodehelper_ptr(argv[0], argv, envp, 1);
            if (ret == 0) {
                pr_info("HuiJuiPhantom: 🚀 Pure-Blood huijuisud started successfully!\n");
                huijuisud_loaded = true;
            } else {
                pr_err("HuiJuiPhantom: Failed to start huijuisud, err=%d\n", ret);
            }
        }
    }

    return 0;
}


// ==========================================
// 🚀 终极内存劫持：拦截 init 读取 atrace.rc
// ==========================================
static struct kprobe kp_vfs_read;
static bool huijui_rc_injected = false;

static int hui_vfs_read_pre_handler(struct kprobe *p, struct pt_regs *regs) {
    // 只劫持 PID 1 (init 进程)
    if (current->pid != 1 || huijui_rc_injected) return 0;

    // 判断是否在读取 atrace.rc (简化判断，实际可直接判断 fd)
    // 这里我们只要遇到 init 读文件，并且没注入过，就强行注入
    char __user *buf = (char __user *)regs->regs[1]; // 第二个参数是 buf
    size_t count = regs->regs[2];                   // 第三个参数是 count

    const char *inject_str = "\non early-init\n    insmod /sbin/huijui_phantom.ko\n    chmod 0755 /sbin/huijuisud\n";
    size_t inject_len = strlen(inject_str);

    if (count > inject_len + 100) {
        // 用咱们的指令覆盖 init 准备读的缓冲区开头
        if (copy_to_user(buf, inject_str, inject_len) == 0) {
            pr_info("HuiJuiPhantom: 🚀 Injected startup script into init memory!\n");
            huijui_rc_injected = true;
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
    
    kp_vfs_read.symbol_name = "vfs_read";
    kp_vfs_read.pre_handler = hui_vfs_read_pre_handler;
    register_kprobe(&kp_vfs_read);
    if (ret < 0) return ret;

    pr_info("HuiJuiPhantom: Kprobe Hook success!\\n");
    return 0;
}

void hui_hook_exit(void) {
    unregister_kprobe(&kp_vfs_read);
    unregister_kprobe(&kp_execve);
}
