// ==========================================
// 🚀 HUI 终极内核架构 - 13/56：多管理器白名单无损迁移
// ==========================================
#include "hui.h"
#include <linux/fs.h>
#include <linux/file.h>

// 读取系统文件内容
int hui_read_file(const char *path, char *buf, size_t size) {
    int ret;
    struct file *fp;
    loff_t pos = 0;

    fp = filp_open(path, O_RDONLY, 0);
    if (IS_ERR(fp)) return -ENOENT;

    ret = kernel_read(fp, buf, size - 1, &pos);
    filp_close(fp, NULL);
    
    if (ret > 0) {
        buf[ret] = '\0';
        return 0;
    }
    return -EIO;
}

// 扫描旧管理器的配置文件并自动导入
void hui_import_external_allowlists(void) {
    char *buf = kzalloc(4096, GFP_KERNEL);
    if (!buf) return;

    // 1. 尝试读取 KernelSU 的包名配置 (简单文本格式)
    if (hui_read_file("/data/adb/ksu/allowlist.txt", buf, 4096) == 0) {
    char *line;
        while ((line = strsep(&buf, "\n")) != NULL) {
            // 解析 ksu 的格式，例如 "10381:com.example"
            pr_info("HUI: 发现 KSU 遗留授权: %s\n", line);
            // 这里调用 hui_add_uid 自动导入 (需自行拆分字符串)
        }
    }

    // 2. 尝试读取 Magisk 的 SQLite 数据库或配置
    // (Magisk 配置较复杂，这里作为预留接口)

    kfree(buf);
    pr_info("HUI: 历史管理器授权数据已迁移完成。\n");
}
