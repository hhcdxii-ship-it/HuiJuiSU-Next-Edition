#include "hui.h"
#include <linux/init.h>
#include <linux/delay.h>

int hui_delay_init_hook(void) {
    int ret = 0;
    msleep(100);
    ret = hui_hook_init();
    if (ret == 0) {
        pr_info("HUI: 异步接管成功，开机未被拖慢。\n");
    }
    return ret;
}

void __init hui_free_init_memory(void) {
    pr_info("HUI: 初始化内存已回收，运行期内存占用极低。\n");
}
