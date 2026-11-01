import os
from pathlib import Path

print("🚀 正在注入 V6 最终形态：ioctl 标准通信 + 稳健加载 + 超管闭环...")

# ==========================================
# 1. 终极内核模块：HuiJuiPhantom.c (包含标准 ioctl)
# ==========================================
kernel_code = '''#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/version.h>
#include <linux/cred.h>
#include <linux/sched.h>
#include <linux/lsm_hooks.h>
#include <linux/security.h>
#include <linux/kallsyms.h>
#include <linux/kprobes.h>

#define DEVICE_NAME "huijui"
#define KSU_CONTEXT "u:r:su:s0"

// 定义标准 ioctl 命令
#define HUIJUI_IOCTL_MAGIC 'H'
#define HUIJUI_IOCTL_GRANT_ROOT _IO(HUIJUI_IOCTL_MAGIC, 1)

static unsigned long (*kallsyms_lookup_name_fn)(const char *name) = NULL;
static int (*security_secctx_to_secid_fn)(const char *secctx, u32 secctxlen, u32 *secid) = NULL;
static u32 ksu_sid = 0;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 7, 0)
static struct kprobe kp = { .symbol_name = "kallsyms_lookup_name" };
#endif

static void resolve_symbols(void) {
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 7, 0)
    register_kprobe(&kp);
    kallsyms_lookup_name_fn = (void *) kp.addr;
    unregister_kprobe(&kp);
#else
    kallsyms_lookup_name_fn = kallsyms_lookup_name;
#endif
    if (kallsyms_lookup_name_fn) {
        security_secctx_to_secid_fn = (void *)kallsyms_lookup_name_fn("security_secctx_to_secid");
    }
}

static int huijui_task_fix_setuid(struct cred *new, const struct cred *old, int flags) {
    if (new->uid.val == 0 && old->uid.val != 0 && ksu_sid != 0) {
        struct task_security_struct *tsec = selinux_cred(new);
        if (tsec) {
            tsec->sid = ksu_sid;
            printk(KERN_INFO "[HuiJuiPhantom] PID %d: SELinux hijacked to su\\n", current->pid);
        }
    }
    return 0;
}

static struct security_hook_list huijui_hooks[] __lsm_ro_after_init = {
    LSM_HOOK_INIT(task_fix_setuid, huijui_task_fix_setuid),
};

// 🔥 标准 ioctl 处理：接受来自 App 的授权指令
static long huijui_ioctl(struct file *file, unsigned int cmd, unsigned long arg) {
    switch (cmd) {
        case HUIJUI_IOCTL_GRANT_ROOT:
            printk(KERN_INFO "[HuiJuiPhantom] User granted root! Escalating current process.\\n");
            // 将当前调用 ioctl 的进程直接提权为 root，并打上 su 标签
            struct cred *new_cred = prepare_creds();
            if (new_cred) {
                new_cred->uid.val = 0;
                new_cred->gid.val = 0;
                new_cred->euid.val = 0;
                new_cred->egid.val = 0;
                struct task_security_struct *tsec = selinux_cred(new_cred);
                if (tsec) tsec->sid = ksu_sid;
                commit_creds(new_cred);
            }
            return 0;
        default:
            return -EINVAL;
    }
}

static const struct file_operations huijui_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = huijui_ioctl,
};

static struct miscdevice huijui_misc_device = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = DEVICE_NAME,
    .fops = &huijui_fops,
};

static int __init huijui_phantom_init(void) {
    resolve_symbols();
    if (security_secctx_to_secid_fn) {
        security_secctx_to_secid_fn(KSU_CONTEXT, strlen(KSU_CONTEXT), &ksu_sid);
    }
    misc_register(&huijui_misc_device);
    security_add_hooks(huijui_hooks, ARRAY_SIZE(huijui_hooks), "huijui");
    printk(KERN_INFO "[HuiJuiPhantom] ✅ Module loaded successfully!\\n");
    return 0;
}

static void __exit huijui_phantom_exit(void) {
    misc_deregister(&huijui_misc_device);
}

module_init(huijui_phantom_init);
module_exit(huijui_phantom_exit);
MODULE_LICENSE("GPL");
'''
Path('kernel/HuiJuiPhantom.c').write_text(kernel_code)
print("✅ HuiJuiPhantom.c 已重写：支持标准 ioctl 授权提权！")


# ==========================================
# 2. 极简且安全的 huijuiinit.c
# ==========================================
init_code = '''#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/stat.h>

int main(int argc, char *argv[]) {
    mkdir("/dev", 0755); mkdir("/proc", 0755); mkdir("/sys", 0755);
    mount("tmpfs", "/dev", "tmpfs", 0, "mode=0755");
    mount("proc", "/proc", "proc", 0, NULL);
    mount("sysfs", "/sys", "sysfs", 0, NULL);
    
    system("insmod /huijui_phantom.ko 2>/dev/null");
    
    char *new_argv[] = { "/init.real", NULL };
    char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
    execve("/init.real", new_argv, envp);
    return 1;
}
'''
Path('core/root/native/huijuiinit/huijuiinit.c').write_text(init_code)
print("✅ huijuiinit.c 已重写：极简防崩安全交权！")


# ==========================================
# 3. C++ 桥接层：jni_wrapper.cpp (标准 ioctl)
# ==========================================
cpp_code = '''#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "HuiJuiJNI", __VA_ARGS__)
#define HUIJUI_DEV "/dev/huijui"
#define HUIJUI_IOCTL_MAGIC 'H'
#define HUIJUI_IOCTL_GRANT_ROOT _IO(HUIJUI_IOCTL_MAGIC, 1)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_huijui_sunext_edition_core_root_HsudInstaller_nativePatch(
        JNIEnv* env, jobject, jstring imagePath, jstring targetPartition, jstring outputPath, jstring initPath, jstring koPath) {
    return JNI_TRUE; // 占位，修补逻辑已由 Kotlin 的 dd 接管
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_huijui_sunext_edition_core_root_SuperUserManager_sendCommandToKernel(
        JNIEnv* env, jobject, jint command_code) {
    int fd = open(HUIJUI_DEV, O_RDWR);
    if (fd < 0) return JNI_FALSE;
    
    int ret = ioctl(fd, command_code);
    close(fd);
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}
'''
Path('core/root/native/shim/jni_wrapper.cpp').write_text(cpp_code)
print("✅ jni_wrapper.cpp 已重写：标准 ioctl 通信！")


# ==========================================
# 4. Kotlin 超管管理器：SuperUserManager.kt
# ==========================================
kt_mgr_code = '''package com.huijui.sunext.edition.core.root

import android.content.Context
import java.io.File

object SuperUserManager {
    private const val PREFS_NAME = "huijui_superuser"
    private const val KEY_ALLOWLIST = "allowlist"
    
    // 必须与内核模块的宏定义完全一致
    const val CMD_GRANT_ROOT = 0x4801
    
    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun getAllowlist(): Set<String> {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs?.getStringSet(KEY_ALLOWLIST, emptySet()) ?: emptySet()
    }

    fun saveAllowlist(list: Set<String>) {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.edit()?.putStringSet(KEY_ALLOWLIST, list)?.apply()
    }

    /**
     * 🔥 闭环：向底层发送提权指令
     */
    fun grantRootToCurrentProcess(): Boolean {
        return sendCommandToKernel(CMD_GRANT_ROOT)
    }

    external fun sendCommandToKernel(commandCode: Int): Boolean
}
'''
Path('app/src/main/java/com/huijui/sunext/edition/core/root/SuperUserManager.kt').write_text(kt_mgr_code)
print("✅ SuperUserManager.kt 已重写：打通 ioctl 权限提升！")
print("🎉 全部完成！请先执行 ./build_all_local.sh 重编内核模块，再执行 ./gradlew assembleDebug")
