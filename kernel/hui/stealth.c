#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/list.h>
#include <linux/kobject.h>
#include <linux/rbtree.h>
#include "hui.h"

// 深度隐身：删除模块在 mod_tree 中的节点（Linux 5.4+）
static void stealth_mod_tree(struct module *mod) {
    // 这里需要通过偏移量直接操作内核数据结构，因为内核没有导出 rb_erase 的符号
    // 代码略（视具体内核版本偏移而定）
}

void hui_deep_stealth(struct module *mod) {
    // 1. 摘除链表
    if (!list_empty(&mod->list)) {
        list_del(&mod->list);
    }
    
    // 2. 隐藏 sysfs
    if (mod->mkobj.kobj.state_initialized) {
        kobject_del(&mod->mkobj.kobj);
    }

    // 3. 清理 mod_tree
    stealth_mod_tree(mod);

    // 4. 篡改模块名（防反作弊通过 string 扫描内存）
    // mod->name 是固定大小数组，不需要判空，直接覆盖
    if (strlen(mod->name) >= 3) {
        char *name = (char *)mod->name;
        name[0] = 'k'; name[1] = 'v'; name[2] = 'm'; // 伪装成内核自带模块
    }
}
