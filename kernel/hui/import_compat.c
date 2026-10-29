#include "hui_compat.h"
#include "hui.h"
#include <linux/fs.h>
#include <linux/file.h>

int hui_read_file(const char *path, char *buf, size_t size) {
    struct file *fp;
    loff_t pos = 0;
    int ret;

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

void hui_import_external_allowlists(void) {
    char *buf = kzalloc(4096, GFP_KERNEL);
    if (!buf) return;
    if (hui_read_file("/data/adb/ksu/allowlist.txt", buf, 4096) == 0) {
        // Parse KSU allowlist
    }
    kfree(buf);
}
