from pathlib import Path

print("🚀 正在重写 huijuiinit.c，改用 finit_module 系统调用...")

path = Path('core/root/native/huijuiinit/huijuiinit.c')

c_code = '''#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <sys/syscall.h>
#include <errno.h>

int main(int argc, char *argv[]) {
    // 1. 挂载基础目录（finit_module 依赖）
    mkdir("/dev", 0755);
    mkdir("/proc", 0755);
    mkdir("/sys", 0755);
    mount("tmpfs", "/dev", "tmpfs", 0, "mode=0755");
    mount("proc", "/proc", "proc", 0, NULL);
    mount("sysfs", "/sys", "sysfs", 0, NULL);

    // 2. 强行通过 finit_module 加载模块（跳过 insmod 工具）
    int fd = open("/huijui_phantom.ko", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        // 0 表示不检查签名（部分内核可能无视此 flag 强制签名）
        syscall(__NR_finit_module, fd, "", 0);
        close(fd);
    }

    // 3. 交权给原厂 init
    char *new_argv[] = { "/init.real", NULL };
    char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
    execve("/init.real", new_argv, envp);
    
    return 1;
}
'''
path.write_text(c_code)
print("✅ huijuiinit.c 已重写为纯系统调用模式！")
