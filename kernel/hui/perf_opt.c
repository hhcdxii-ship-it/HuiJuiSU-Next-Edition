#include "hui.h"
#include <linux/limits.h>
#include <linux/jiffies.h>

void hui_opt_log_execve(const char *comm) {
    pr_info_ratelimited("HUI: 高频拦截 execve [%s]\n", comm);
}

bool hui_fast_uid_check(uid_t uid) {
    if (hui_state.app_count == 1) {
        struct hui_app_info *single_app;
        hlist_for_each_entry_rcu(single_app, &hui_allowlist[0], hash_node) {
            if (single_app->uid == uid) return true;
        }
    }
    return false;
}
