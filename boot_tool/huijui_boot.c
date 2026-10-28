#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Android Boot Header (简化版，覆盖 V3/V4 的通用核心)
struct boot_img_hdr {
    unsigned char magic[8];
    unsigned int kernel_size;
    unsigned int ramdisk_size;
    unsigned int os_version;
    // ... 其他字段根据具体版本补充
};

void print_usage() {
    printf("HuiJuiSU Pure-Blood Boot Tool v1.0\n");
    printf("Usage:\n");
    printf("  huijui_boot unpack <boot.img> <out_dir>\n");
    printf("  huijui_boot repack <in_dir> <boot.img>\n");
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        print_usage();
        return 1;
    }

    const char *cmd = argv[1];
    const char *input = argv[2];
    const char *output = argv[3];

    if (strcmp(cmd, "unpack") == 0) {
        printf(">>> [纯血解包] 正在解析 %s ...\n", input);
        FILE *fp = fopen(input, "rb");
        if (!fp) {
            perror("打开镜像失败");
            return 1;
        }

        struct boot_img_hdr hdr;
        fread(&hdr, sizeof(hdr), 1, fp);

        // 简单的魔数校验 (Android boot magic 通常是 "ANDROID!")
        if (memcmp(hdr.magic, "ANDROID!", 8) != 0) {
            printf("❌ 错误：不是标准的 Android Boot 镜像！\n");
            fclose(fp);
            return 1;
        }

        printf("✅ 识别到 Boot 镜像！\n");
        printf("   Kernel Size: %u\n", hdr.kernel_size);
        printf("   Ramdisk Size: %u\n", hdr.ramdisk_size);

        // 这里留给你：用 C 提取 kernel 和 ramdisk 到输出目录
        // fwrite(...)

        fclose(fp);
    } else if (strcmp(cmd, "repack") == 0) {
        printf(">>> [纯血重打包] 正在生成 %s ...\n", output);
        // 这里留给你：读取目录里的 kernel 和 ramdisk，重新写入 header
    } else {
        print_usage();
    }

    return 0;
}
