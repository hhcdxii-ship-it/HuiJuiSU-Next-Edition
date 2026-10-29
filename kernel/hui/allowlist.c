// ==========================================
// 🚀 HUI 终极内核架构 - 1/56：授权引擎
// ==========================================
#include "hui.h"

// 全局哈希表和锁
struct hlist_head hui_allowlist[HUI_HASH_BITS];
spinlock_t hui_allowlist_lock;

// 释放节点的 RCU 回调
void hui_free_app_info(struct rcu_head *rcu) {
    struct hui_app_info *app = container_of(rcu, struct hui_app_info, rcu);
    kfree(app);
}

// 内部函数：根据 UID 查找节点（需在锁内或 RCU 保护下调用）
static struct hui_app_info *hui_find_app_locked(uid_t uid) {
    struct hui_app_info *app;
    hash_for_each_possible(hui_allowlist, app, hash_node, uid) {
        if (app->uid == uid) {
            return app;
        }
    }
    return NULL;
}

// 外部函数：检查 UID 是否被授权（无锁快路径，RCU保护）
bool hui_is_uid_allowed(uid_t uid) {
    struct hui_app_info *app;
    bool found = false;

    if (uid == 0) return true; // root 永远放行
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

    if (uid == 0 || uid == -1) return -EINVAL;
    if (!pkg_name || strlen(pkg_name) == 0) return -EINVAL;

    new_app = kzalloc(sizeof(struct hui_app_info), GFP_KERNEL);
    if (!new_app) return -ENOMEM;

    new_app->uid = uid;
    strncpy(new_app->package_name, pkg_name, HUI_MAX_PACKAGE_LEN - 1);
    atomic_set(&new_app->ref_count, 1);

    spin_lock(&hui_allowlist_lock);
    existing = hui_find_app_locked(uid);
    if (existing) {
        spin_unlock(&hui_allowlist_lock);
        kfree(new_app); // 已存在，直接丢弃新建的
        return -EEXIST;
    }
    hash_add_rcu(hui_allowlist, &new_app->hash_node, uid);
    hui_state.app_count++;
    spin_unlock(&hui_allowlist_lock);

    pr_info("HUI: 已授权 UID [%d] (%s)\n", uid, pkg_name);
    return 0;
}

// 外部函数：移除授权
void hui_remove_uid(uid_t uid) {
    struct hui_app_info *app;

    spin_lock(&hui_allowlist_lock);
    app = hui_find_app_locked(uid);
    if (app) {
        hash_del_rcu(&app->hash_node);
        hui_state.app_count--;
        spin_unlock(&hui_allowlist_lock);
        call_rcu(&app->rcu, hui_free_app_info);
        pr_info("HUI: 已撤销 UID [%d] 的授权\n", uid);
    } else {
        spin_unlock(&hui_allowlist_lock);
    }
}

// 外部函数：清空所有授权（卸载模块时调用）
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
    pr_info("HUI: 授权引擎已清空。\n");
}
