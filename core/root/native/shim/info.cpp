#include "base.hpp"
#include "bootimg.hpp"
#include "format.hpp"

int do_info(const char *image) {
    mmap_data m(image);
    if (!m.buf) {
        LOGE("cannot open %s\n", image);
        return 1;
    }

    format_t fmt = check_fmt(m.buf, m.sz);
    LOGI("format=%s\n", fmt2name[fmt]);

    if (fmt == AOSP || fmt == AOSP_VENDOR || fmt == BLOB || fmt == DHTB || fmt == CHROMEOS) {
        boot_img boot(image);
        LOGI("header_version=%u\n", boot.hdr->header_version());
        LOGI("kernel_size=%u\n", boot.hdr->kernel_size());
        LOGI("ramdisk_size=%u\n", boot.hdr->ramdisk_size());
        LOGI("cmdline=%s\n", boot.hdr->cmdline());
        LOGI("os_version=0x%08x\n", boot.hdr->os_version());
    } else if (COMPRESSED_ANY(fmt)) {
        LOGI("compressed_size=%zu\n", m.sz);
    }

    LOGI("result=ok\n");
    return 0;
}
