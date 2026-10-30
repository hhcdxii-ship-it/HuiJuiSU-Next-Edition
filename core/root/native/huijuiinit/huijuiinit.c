#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <string.h>
#include <errno.h>

void klog(const char *fmt, ...) {
    int fd = open("/dev/kmsg", O_WRONLY);
    if (fd >= 0) {
        va_list ap;
        va_start(ap, fmt);
        vdprintf(fd, fmt, ap);
        va_end(ap);
        close(fd);
    }
}

// 核心挂载逻辑（抄袭 KernelSU 的 init 机制）
void mount_devtmpfs() {
    mkdir("/dev", 0755);
    mount("devtmpfs", "/dev", "devtmpfs", 0, NULL);
}

int main(int argc, char *argv[]) {
    klog("huijuiinit: Starting hijack...\n");

    // 1. 必须挂载 /dev 和 /proc，原厂 init 极度依赖
    mount_devtmpfs();
    mkdir("/proc", 0755);
    mount("proc", "/proc", "proc", 0, NULL);

    // 2. 强行加载咱们的内核模块！
    int fd = open("/sbin/huijui_phantom.ko", O_RDONLY);
    if (fd >= 0) {
        int ret = syscall(__NR_finit_module, fd, "", 0);
        close(fd);
        if (ret == 0) {
            klog("huijuiinit: huijui_phantom.ko loaded SUCCESS!\n");
        } else {
            klog("huijuiinit: failed to load ko, errno=%d\n", errno);
        }
    } else {
        klog("huijuiinit: cannot open ko file!\n");
    }

    // 3. 交还控制权，极其重要的环境变量原样传递！
    klog("huijuiinit: Handing over to /init.real...\n");
    
    char *new_argv[] = { "/init.real", NULL };
    // 🚀 传特定的环境变量，绝不能瞎传
    char *new_envp[] = { 
        "PATH=/sbin:/system/sbin:/system/bin:/system/xbin",
        "ANDROID_ROOT=/system",
        "ANDROID_DATA=/data",
        NULL 
    };
    
    execve("/init.real", new_argv, new_envp);
    
    klog("huijuiinit: execve /init.real FAILED!\n");
    return 1;
}
