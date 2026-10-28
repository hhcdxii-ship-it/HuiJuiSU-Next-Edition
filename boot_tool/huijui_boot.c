// ==========================================
// 🚀 HUI 终极内核架构 - 31/56：镜像格式嗅探引擎
// ==========================================
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define BOOT_MAGIC "ANDROID!"
#define VENDOR_BOOT_MAGIC "VNDRBOOT"
#define DTBO_MAGIC 0xD7B7AB1E
#define SPARSE_MAGIC 0x3AFF26ED

// 读取文件头，返回对应的分区类型
const char* detect_image_type(const char *filepath) {
    FILE *fp = fopen(filepath, "rb");
    if (!fp) return "ERROR_CANNOT_OPEN";

    uint8_t header[16] = {0};
    fread(header, 1, 16, fp);

    // 1. 检测标准 Boot 镜像 (v0-v2, 以及 v3-v4 魔数一样)
    if (memcmp(header, BOOT_MAGIC, 8) == 0) {
        fclose(fp);
        // 在实际项目中可以进一步解析 header version 和 ramdisk 大小
        // 如果 ramdisk 大小为 0，通常是纯 kernel，应该是 boot；如果包含 ramdisk 且机器有 init_boot，那可能是 init_boot
        return "boot_or_init_boot"; 
    }

    // 2. 检测 Vendor Boot 镜像
    if (memcmp(header, VENDOR_BOOT_MAGIC, 8) == 0) {
        fclose(fp);
        return "vendor_boot";
    }

    // 3. 检测 DTBO (设备树镜像)
    uint32_t magic32 = *(uint32_t*)header;
    if (magic32 == DTBO_MAGIC) {
        fclose(fp);
        return "dtbo";
    }

    // 4. 检测 Sparse (稀疏镜像，即被压缩过的分区)
    if (magic32 == SPARSE_MAGIC) {
        fclose(fp);
        return "sparse_image";
    }

    // 5. 检测 Recovery 或其他 (通常也是 ANDROID! 魔数)
    fclose(fp);
    return "unknown";
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        printf("Usage: huijui_boot <detect|flash> <filepath> [partition]\n");
        return 1;
    }

    const char *cmd = argv[1];
    const char *file = argv[2];

    if (strcmp(cmd, "detect") == 0) {
        const char *type = detect_image_type(file);
        printf("%s\n", type);
        return 0;
    }

    if (strcmp(cmd, "flash") == 0) {
        if (argc < 4) {
            printf("Missing partition target!\n");
            return 1;
        }
        const char *target = argv[3];
        char cmd_buf[512];
        printf("Flashing %s to /dev/block/by-name/%s...\n", file, target);
        
        // 使用系统底层 dd 命令写入，强制同步
        snprintf(cmd_buf, sizeof(cmd_buf), "dd if=%s of=/dev/block/by-name/%s bs=4M && sync", file, target);
        int ret = system(cmd_buf);
        if (ret == 0) printf("SUCCESS\n");
        else printf("FAILED\n");
        return ret;
    }

    return 0;
}
