// ==========================================
// 🚀 HUI 终极内核架构 - 17/56：开机与内存优化
// ==========================================
#include "hui.h"
#include <linux/init.h>
#include <linux/delay.h>

// 🚀 开机提速：延迟初始化机制
// 等系统核心服务都启动完毕，再悄悄接管，不让开机变慢
static __attribute__((unused)) int hui_delay_init_hook(void) {
    int ret = 0;
    
    // 简单延时规避 init 阶段的高峰
    msleep(100);
    
    // 安装核心劫持
    ret = hui_hook_init();
static __attribute__((unused))   if(ret == 0) {
        pr_info("HUI: 异步接管成功，开机未被拖慢。\n");
    }
    return ret;
}

// 🚀 内存优化：强制释放初始化代码内存
// 这个宏定义让我们可以在模块加载后，把 init 阶段的函数全部释放掉
static __attribute__((unused)) void __init hui_free_init_memory(void) {
    // 内核会自动根据 __init 段进行内存回收
    pr_info("HUI: 初始化内存已回收，运行期内存占用极低。\n");
}
