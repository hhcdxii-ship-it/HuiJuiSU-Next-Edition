// ==========================================
// 🚀 HUI 纯血核心内核模块 (终极稳定版)
// 修复宏污染与 pt_regs 兼容性，专注 execve 劫持
// ==========================================
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/syscalls.h>
#include <linux/kallsyms.h>
#include <linux/kprobes.h>
#include <linux/cred.h>
#include <linux/sched.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/hashtable.h>
#include <linux/spinlock.h>
#include <linux/slab.h>
#include <linux/rcupdate.h>

MODULE_LICENSE("GPL");
MODULE_AUTHOR("HuiJuiSU Team");
MODULE_DESCRIPTION("HuiJuiSU Ultimate HUI Core");

#define DEVICE_NAME "huijui"
// 🚀 修复 1：重命名宏，绝不污染内核自带的 HASH_BITS
#define HIJ_HASH_BITS 8

struct huijui_allow_entry {
    uid_t uid;
    char package_name[256];
    struct hlist_node node;
};

static unsigned long *sys_call_table_ptr = NULL;
static asmlinkage long (*original_execve)(const struct pt_regs *regs);

static DEFINE_HASHTABLE(huijui_allowlist, HIJ_HASH_BITS);
static DEFINE_SPINLOCK(huijui_lock);
static unsigned int authorized_count = 0;
static bool hui_module_loaded = false;

// 查找 UID 是否在授权列表
static bool is_uid_allowed(uid_t uid) {
    struct huijui_allow_entry *entry;
    bool found = false;
    
    rcu_read_lock();
    hash_for_each_possible_rcu(huijui_allowlist, entry, node, uid) {
        if (entry->uid == uid) {
            found = true;
            break;
        }
    }
    rcu_read_unlock();
    
    return found;
}

// 添加 UID
static void add_uid_to_allowlist(uid_t uid, const char *pkg_name) {
    struct huijui_allow_entry *new_entry;
    if (is_uid_allowed(uid)) return;

    new_entry = kmalloc(sizeof(struct huijui_allow_entry), GFP_KERNEL);
    if (!new_entry) return;
    
    new_entry->uid = uid;
    if (pkg_name) {
        strncpy(new_entry->package_name, pkg_name, sizeof(new_entry->package_name) - 1);
    } else {
        strcpy(new_entry->package_name, "unknown");
    }

    spin_lock(&huijui_lock);
    hash_add_rcu(huijui_allowlist, &new_entry->node, uid);
    authorized_count++;
    spin_unlock(&huijui_lock);
    pr_info("HUI: 已授权 UID [%d] 包名 [%s]\n", uid, new_entry->package_name);
}

// 字符设备写入接口
static ssize_t huijui_write(struct file *file, const char __user *buf, size_t count, loff_t *ppos) {
    char kbuf[300];
    int uid;
    char pkg[256];
    char *colon_pos;

    if (current_uid().val != 0) {
        return -EPERM;
    }

    if (count >= sizeof(kbuf)) return -EINVAL;
    if (copy_from_user(kbuf, buf, count)) return -EFAULT;
    kbuf[count] = '\0';

    colon_pos = strchr(kbuf, ':');
    if (colon_pos) {
        *colon_pos = '\0';
        if (kstrtoint(kbuf, 10, &uid) == 0) {
            strncpy(pkg, colon_pos + 1, sizeof(pkg) - 1);
            pkg[strcspn(pkg, "\r\n")] = 0;
            add_uid_to_allowlist(uid, pkg);
        }
    }
    return count;
}

static const struct file_operations huijui_fops = {
    .owner = THIS_MODULE,
    .write = huijui_write,
};

static struct miscdevice huijui_misc_dev = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = DEVICE_NAME,
    .fops = &huijui_fops,
    .mode = 0666,
};

// 提权函数
static void give_root_safe(void) {
    struct cred *new_cred;
    if (!current->mm) return; // 防止中断上下文死机
    new_cred = prepare_kernel_cred(NULL);
    if (new_cred) {
        commit_creds(new_cred);
        pr_info("HUI: 进程 [%s] 成功提权!\n", current->comm);
    }
}

// 劫持 execve
static asmlinkage long hooked_execve(const struct pt_regs *regs) {
    uid_t uid = current_uid().val;
    if (uid != 0 && is_uid_allowed(uid)) {
        give_root_safe();
    }
    return original_execve(regs);
}

// Kprobe 寻址
static unsigned long *get_sys_call_table_addr(void) {
    struct kprobe kp = { .symbol_name = "kallsyms_lookup_name" };
    unsigned long *table = NULL;
    typedef unsigned long (*kln_t)(const char *name);
    kln_t kln;

    if (register_kprobe(&kp) < 0) return NULL;
    kln = (kln_t) kp.addr;
    unregister_kprobe(&kp);

    if (kln) {
        table = (unsigned long *)kln("sys_call_table");
    }
    return table;
}

// 写保护操作（ARM64）
static inline void disable_wp(void) {
    unsigned long val;
    asm volatile("mrs %0, sctlr_el1" : "=r" (val));
    val &= ~(1 << 19);
    asm volatile("msr sctlr_el1, %0" : : "r" (val));
    asm volatile("isb");
}

static inline void enable_wp(void) {
    unsigned long val;
    asm volatile("mrs %0, sctlr_el1" : "=r" (val));
    val |= (1 << 19);
    asm volatile("msr sctlr_el1, %0" : : "r" (val));
    asm volatile("isb");
}

static int __init huijui_init(void) {
    int ret;
    if (hui_module_loaded) return 0;

    pr_info("HUI: 纯血内核模块正在注入...\n");

    ret = misc_register(&huijui_misc_dev);
    if (ret) return ret;

    sys_call_table_ptr = get_sys_call_table_addr();
    if (!sys_call_table_ptr) {
        misc_deregister(&huijui_misc_dev);
        return -EFAULT;
    }

    original_execve = (void *)sys_call_table_ptr[__NR_execve];

    disable_wp();
    sys_call_table_ptr[__NR_execve] = (unsigned long)hooked_execve;
    enable_wp();

    hui_module_loaded = true;
    pr_info("HUI: 接管成功！\n");
    return 0;
}

static void __exit huijui_exit(void) {
    struct huijui_allow_entry *entry;
    struct hlist_node *tmp;
    int bkt;

    if (!hui_module_loaded) return;

    if (sys_call_table_ptr && original_execve) {
        disable_wp();
        sys_call_table_ptr[__NR_execve] = (unsigned long)original_execve;
        enable_wp();
    }

    misc_deregister(&huijui_misc_dev);

    spin_lock(&huijui_lock);
    hash_for_each_safe(huijui_allowlist, bkt, tmp, entry, node) {
        hash_del_rcu(&entry->node);
        kfree(entry);
    }
    authorized_count = 0;
    spin_unlock(&huijui_lock);

    hui_module_loaded = false;
    pr_info("HUI: 卸载完成。\n");
}

module_init(huijui_init);
module_exit(huijui_exit);
