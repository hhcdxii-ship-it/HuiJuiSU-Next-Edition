#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/hashtable.h>
#include <linux/jiffies.h>
#include <linux/workqueue.h>
#include <linux/jump_label.h>  // 🚀 引入 static_key 头文件
#include "hui.h"

// 🚀 功耗优化：static_key，没有临时授权时开销为 0
static DEFINE_STATIC_KEY_FALSE(temp_root_active);

static DEFINE_HASHTABLE(temp_grants, 8);
static DEFINE_SPINLOCK(temp_lock);
static struct delayed_work cleanup_work;

// 🚀 修正：结构体完整定义，防止 incomplete type
struct hui_temp_grant {
    struct hlist_node node;
    uid_t uid;
    unsigned long expires_at;
};

// 检查该 UID 是否拥有有效的临时授权
bool hui_check_temp_root(uid_t uid) {
    struct hui_temp_grant *grant;
    unsigned long now = jiffies; 
    bool valid = false;

    // 🚀 如果没启用临时授权，直接秒返回，不拿锁
    if (!static_branch_unlikely(&temp_root_active)) return false;

    spin_lock(&temp_lock);
    hash_for_each_possible(temp_grants, grant, node, uid) {
        if (grant->uid == uid) {
            if (time_before(now, grant->expires_at)) {
                valid = true;
                break;
            }
        }
    }
    spin_unlock(&temp_lock);
    return valid;
}

// 下发临时授权
int hui_grant_temp_root(uid_t uid, unsigned long timeout_seconds) {
    struct hui_temp_grant *grant;
    unsigned long expires = jiffies + (timeout_seconds * HZ); 

    spin_lock(&temp_lock);
    hash_for_each_possible(temp_grants, grant, node, uid) {
        if (grant->uid == uid) {
            hash_del(&grant->node);
            kfree(grant);
            break;
        }
    }

    grant = kmalloc(sizeof(*grant), GFP_ATOMIC);
    if (!grant) {
        spin_unlock(&temp_lock);
        return -ENOMEM;
    }
    grant->uid = uid;
    grant->expires_at = expires;
    hash_add(temp_grants, &grant->node, uid);
    
    // 🚀 开启 static_key
    static_branch_enable(&temp_root_active);
    spin_unlock(&temp_lock);

    pr_debug("HuiJuiPhantom: Granted temp root to uid %d for %lu sec\n", uid, timeout_seconds);
    return 0;
}

// 撤销所有
void hui_revoke_all_temp_roots(void) {
    struct hui_temp_grant *grant;
    int bkt;
    unsigned long flags;

    // 🚀 关闭 static_key
    static_branch_disable(&temp_root_active);

    spin_lock_irqsave(&temp_lock, flags);
    hash_for_each(temp_grants, bkt, grant, node) {
        hash_del(&grant->node);
        kfree(grant);
    }
    spin_unlock_irqrestore(&temp_lock, flags);
}

// 后台清理工作
static void cleanup_worker(struct work_struct *work) {
    struct hui_temp_grant *grant;
    int bkt;
    unsigned long flags;

    spin_lock_irqsave(&temp_lock, flags);
    hash_for_each(temp_grants, bkt, grant, node) {
        if (time_after(jiffies, grant->expires_at)) {
            hash_del(&grant->node);
            kfree(grant);
        }
    }
    spin_unlock_irqrestore(&temp_lock, flags);

    schedule_delayed_work(&cleanup_work, 10 * HZ);
}

int hui_temp_grant_init(void) {
    hash_init(temp_grants);
    INIT_DELAYED_WORK(&cleanup_work, cleanup_worker);
    schedule_delayed_work(&cleanup_work, 10 * HZ);
    return 0;
}

void hui_temp_grant_exit(void) {
    cancel_delayed_work_sync(&cleanup_work);
    hui_revoke_all_temp_roots();
}
