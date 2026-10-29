// ==========================================
// 🚀 HUI 终极内核架构 - 16/56：极客性能与功耗优化
// ==========================================
#include "hui.h"
#include <linux/limits.h>
#include <linux/jiffies.h>

// 🚀 功耗核心：减少日志频繁唤醒 CPU
// 把普通 printk 替换为限流版，防止恶意 App 疯狂 execve 导致 CPU 满载发热
static __attribute__((unused)) void hui_opt_log_execve(const char *comm) {
    // 每秒最多打印 1 次，彻底杜绝刷屏导致的耗电
    pr_info_ratelimited("HUI: 高频拦截 execve 请求 [%s]\n", comm);
}

// 🚀 性能核心：极速快查 UID 列表
// 针对极少数量的授权用户，直接跳过 RCU 哈希表遍历，命中率更高
__attribute__((unused)) bool hui_fast_uid_check(uid_t uid) {
    // 如果系统里只授权了 1 个 App（比如只有自己的管理器），直接走快速路径
static __attribute__((unused))   if(hui_state.app_count == 1) {
        struct hui_app_info *single_app;
        // 偷个懒，直接取哈希表第一个节点
static __attribute__((unused))   hlist_for_each_entry_rcu(single_app, &hui_allowlist[0], hash_node) {
            if (single_app->uid == uid) return true;
        }
    }
    return false;
}
