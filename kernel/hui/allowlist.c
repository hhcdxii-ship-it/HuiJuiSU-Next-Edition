#include "hui.h"

// 全局哈希表和锁
struct hlist_head hui_allowlist[HUI_HASH_BITS];
spinlock_t hui_allowlist_lock;

// 释放节点的 RCU 回调 (内部函数，必须加 static)
static void hui_free_app_info(struct rcu_head *rcu) {
    kfree(container_of(rcu, struct hui_app_info, rcu));
}

// 内部函数：根据 UID 查找节点 (必须加 static)
static struct hui_app_info *hui_find_app_locked(uid_t uid) {
    struct hui_app_info *app;
    hash_for_each_possible(hui_allowlist, app, hash_node, uid) {
        if (app->uid == uid) return app;
    }
    return NULL;
}

// 外部函数：检查 UID 是否被授权
bool hui_is_uid_allowed(uid_t uid) {
    struct hui_app_info *app;
    bool found = false;

    if (uid == 0) return true;
    if (uid == -1) return false;

    rcu_read_lock();
    hash_for_each_possible_rcu(hui_allowlist, app, hash_node, uid) {
        if (app->uid == uid) {
            found = true;
            break;
        }
    }
    rcu_read_unlock();
    return found;
}

// 外部函数：添加授权
int hui_add_uid(uid_t uid, const char *pkg_name) {
    struct hui_app_info *new_app, *existing;

    if (uid == 0 || !pkg_name) return -EINVAL;

    new_app = kzalloc(sizeof(struct hui_app_info), GFP_KERNEL);
    if (!new_app) return -ENOMEM;

    new_app->uid = uid;
    strncpy(new_app->package_name, pkg_name, HUI_MAX_PACKAGE_LEN - 1);
    atomic_set(&new_app->ref_count, 1);

    spin_lock(&hui_allowlist_lock);
    existing = hui_find_app_locked(uid);
    if (existing) {
        spin_unlock(&hui_allowlist_lock);
        kfree(new_app);
        return -EEXIST;
    }
    hash_add_rcu(hui_allowlist, &new_app->hash_node, uid);
    hui_state.app_count++;
    spin_unlock(&hui_allowlist_lock);

    pr_info("HuiJuiPhantom: 已授权 UID [%d] (%s)\n", uid, pkg_name);
    return 0;
}

// 外部函数：移除授权 (补全之前消失的实现)
void hui_remove_uid(uid_t uid) {
    struct hui_app_info *app;

    spin_lock(&hui_allowlist_lock);
    app = hui_find_app_locked(uid);
    if (app) {
        hash_del_rcu(&app->hash_node);
        hui_state.app_count--;
        spin_unlock(&hui_allowlist_lock);
        call_rcu(&app->rcu, hui_free_app_info);
        pr_info("HuiJuiPhantom: 已撤销 UID [%d] 的授权\n", uid);
    } else {
        spin_unlock(&hui_allowlist_lock);
    }
}

// 外部函数：清空所有授权
void hui_destroy_allowlist(void) {
    struct hui_app_info *app;
    struct hlist_node *tmp;
    int bkt;

    spin_lock(&hui_allowlist_lock);
    hash_for_each_safe(hui_allowlist, bkt, tmp, app, hash_node) {
        hash_del_rcu(&app->hash_node);
        call_rcu(&app->rcu, hui_free_app_info);
    }
    hui_state.app_count = 0;
    spin_unlock(&hui_allowlist_lock);
    synchronize_rcu();
    pr_info("HuiJuiPhantom: 授权引擎已清空。\n");
}
