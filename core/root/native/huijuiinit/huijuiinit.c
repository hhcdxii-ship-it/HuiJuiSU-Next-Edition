#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <sys/sysmacros.h>

int main(int argc, char *argv[]) {
    // 1. 早期挂载
    mount("proc", "/proc", "proc", 0, NULL);
    mount("sysfs", "/sys", "sysfs", 0, NULL);
    mount("devtmpfs", "/dev", "devtmpfs", 0, "mode=0755");
    
    // 2. 亲自出马，执行 force_insmod 加载纯血模块
    pid_t insmod_pid = fork();
    if (insmod_pid == 0) {
        setsid();
        // 注意：这里的路径是 ramdisk 的根目录
        execl("/force_insmod", "force_insmod", "/huijui_phantom.ko", NULL);
        _exit(1);
    }
    int status;
    waitpid(insmod_pid, &status, 0); // 必须等它插完再继续

    // 3. 拉起 huijuisud 守护进程
    pid_t pid = fork();
    if (pid == 0) {
        setsid();
        char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
        execve("/huijuisud", (char *[]){"/huijuisud", "daemon", NULL}, envp);
        _exit(1);
    }

    // 4. 执行真正的系统 init
    const char *paths[] = { "/init.real", "/system/bin/init", NULL };
    char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
    for (int i = 0; paths[i]; i++) {
        if (access(paths[i], F_OK) == 0) {
            execve(paths[i], (char *[]){ (char *)paths[i], NULL }, envp);
        }
    }
    return 1;
}
