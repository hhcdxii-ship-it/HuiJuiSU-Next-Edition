#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <string.h>
#include <errno.h>

// 日志打到内核层，方便通过 dmesg 抓取
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

int main(int argc, char *argv[]) {
    klog("huijuiinit: Starting boot injection...\n");

    // 1. 挂载基础文件系统 (防范未挂载的情况)
    mkdir("/proc", 0755); mkdir("/sys", 0755); mkdir("/dev", 0755); mkdir("/sbin", 0755);
    mount("proc", "/proc", "proc", 0, NULL);
    mount("sysfs", "/sys", "sysfs", 0, NULL);
    mount("devtmpfs", "/dev", "devtmpfs", 0, NULL);

    // 2. 加载纯血内核模块 .ko
    const char *ko_path = "/sbin/huijui_phantom.ko";
    int fd = open(ko_path, O_RDONLY);
    if (fd >= 0) {
        // 使用 finit_module 系统调用直接加载 .ko
        int ret = syscall(__NR_finit_module, fd, "", 0);
        close(fd);
        if (ret == 0) {
            klog("huijuiinit: huijui_phantom.ko loaded SUCCESS!\n");
        } else {
            klog("huijuiinit: failed to load ko, errno=%d\n", errno);
        }
    } else {
        klog("huijuiinit: ko file not found at %s\n", ko_path);
    }

    // 3. 🚀 拉起咱们的纯血守护进程 huijuisud
    klog("huijuiinit: Starting huijuisud daemon...\n");
    if (fork() == 0) {
        execl("/sbin/huijuisud", "huijuisud", NULL);
        klog("huijuiinit: exec huijuisud FAILED!\n");
        exit(1);
    }

    // 4. 将控制权交还给原厂 init (被咱们改名为 init.real)
    klog("huijuiinit: Handing over to /init.real...\n");
    
    char *new_argv[] = { "/init.real", NULL };
    char *new_envp[] = { "PATH=/sbin:/system/sbin:/system/bin:/system/xbin", NULL };
    
    // 执行原厂 init
    execve("/init.real", new_argv, new_envp);
    
    // 如果走到这里，说明 execve 失败了
    klog("huijuiinit: execve /init.real FAILED!\n");
    return 1;
}
