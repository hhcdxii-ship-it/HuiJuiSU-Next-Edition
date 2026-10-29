#include "hui_compat.h"
#include "hui.h"
#include <linux/fs.h>
#include <linux/file.h>

int hui_check_selinux_status(void) {
    struct file *fp;
    char buf[16] = {0};
    loff_t pos = 0;
    int ret;

    fp = filp_open("/sys/fs/selinux/enforce", O_RDONLY, 0);
    if (IS_ERR(fp)) return 0;
    ret = kernel_read(fp, buf, sizeof(buf) - 1, &pos);
    filp_close(fp, NULL);
    if (ret > 0 && buf[0] == '1') return 1;
    return 0;
}

void hui_selinux_log_avc(void) {
}
