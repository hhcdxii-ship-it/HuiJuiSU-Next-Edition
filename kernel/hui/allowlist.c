#include "hui.h"
struct hlist_head hui_allowlist[HUI_HASH_BITS];
spinlock_t hui_allowlist_lock;
void hui_free_app_info(struct rcu_head *rcu) { kfree(container_of(rcu, struct hui_app_info, rcu)); }
struct hui_app_info *hui_find_app_locked(uid_t uid) {
    struct hui_app_info *app;
    hash_for_each_possible(hui_allowlist, app, hash_node, uid) { if (app->uid == uid) return app; }
    return NULL;
}
bool hui_is_uid_allowed(uid_t uid) {
    struct hui_app_info *app; bool found = false;
    if (uid == 0) return true;
    rcu_read_lock();
    hash_for_each_possible_rcu(hui_allowlist, app, hash_node, uid) { if (app->uid == uid) { found = true; break; } }
    rcu_read_unlock();
    return found;
}
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
    if (existing) { spin_unlock(&hui_allowlist_lock); kfree(new_app); return -EEXIST; }
    hash_add_rcu(hui_allowlist, &new_app->hash_node, uid);
    hui_state.app_count++;
    spin_unlock(&hui_allowlist_lock);
    return 0;
}
void hui_destroy_allowlist(void) {
    struct hui_app_info *app; struct hlist_node *tmp; int bkt;
    spin_lock(&hui_allowlist_lock);
    hash_for_each_safe(hui_allowlist, bkt, tmp, app, hash_node) { hash_del_rcu(&app->hash_node); call_rcu(&app->rcu, hui_free_app_info); }
    hui_state.app_count = 0;
    spin_unlock(&hui_allowlist_lock);
    synchronize_rcu();
}
