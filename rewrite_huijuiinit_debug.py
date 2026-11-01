from pathlib import Path

print("🚀 正在重写 huijuiinit.c，加入内核日志探针...")

path = Path('core/root/native/huijuiinit/huijuiinit.c')

c_code = '''#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>

void klog(const char* msg) {
    int fd = open("/dev/kmsg", O_WRONLY);
    if (fd >= 0) {
        write(fd, msg, strlen(msg));
        close(fd);
    }
}

int main(int argc, char *argv[]) {
    klog("HUIJUI_INIT: Started! Hijacked successfully.\\n");
    
    mkdir("/dev", 0755);
    mkdir("/proc", 0755);
    mkdir("/sys", 0755);
    mount("tmpfs", "/dev", "tmpfs", 0, "mode=0755");
    mount("proc", "/proc", "proc", 0, NULL);
    mount("sysfs", "/sys", "sysfs", 0, NULL);
    
    klog("HUIJUI_INIT: Mounts done, trying insmod...\\n");
    
    int ret = system("insmod /huijui_phantom.ko");
    if (ret != 0) {
        klog("HUIJUI_INIT: insmod failed! (SELinux or signature block)\\n");
        // 尝试在 /data/local/tmp 再捞一次（如果目录已挂载）
        system("cp /huijui_phantom.ko /data/local/tmp/ 2>/dev/null");
        ret = system("insmod /data/local/tmp/huijui_phantom.ko 2>/dev/null");
        if (ret == 0) {
            klog("HUIJUI_INIT: insmod success from /data/local/tmp!\\n");
        }
    } else {
        klog("HUIJUI_INIT: insmod success from ramdisk!\\n");
    }

    klog("HUIJUI_INIT: handing over to /init.real...\\n");
    
    char *new_argv[] = { "/init.real", NULL };
    char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
    execve("/init.real", new_argv, envp);
    
    klog("HUIJUI_INIT: FATAL! execve /init.real failed!\\n");
    return 1;
}
'''
path.write_text(c_code)
print("✅ huijuiinit.c 已重写，探针已注入！")
