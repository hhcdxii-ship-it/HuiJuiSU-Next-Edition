#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <errno.h>
#include <sys/prctl.h>
#include <sys/types.h>

#define ABSTRACT_SOCKET_NAME "\0huijui_ghost_socket"
#define APP_PACKAGE_NAME "com.huijui.sunext.edition"

// 🚀 终极校验：检查连进来的进程是不是我们自己的 App
int is_our_app(int client_fd) {
    struct ucred cred;
    socklen_t len = sizeof(struct ucred);
    
    // 获取客户端凭证
    if (getsockopt(client_fd, SOL_SOCKET, SO_PEERCRED, &cred, &len) == -1) {
        return 0;
    }
    
    // 如果连进来的是 root 或者 system，直接放行（比如你的 adb shell）
    if (cred.uid == 0 || cred.uid == 1000) return 1;

    // 否则，去 /proc/pid/cmdline 里查包名
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", cred.pid);
    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        char cmdline[256] = {0};
        read(fd, cmdline, sizeof(cmdline) - 1);
        close(fd);
        
        // 如果 cmdline 包含我们的包名，才允许执行
        if (strstr(cmdline, APP_PACKAGE_NAME) != NULL) {
            return 1;
        }
    }
    return 0;
}

void execute_cmd(int client_fd) {
    char buffer[4096] = {0};
    int n = read(client_fd, buffer, sizeof(buffer) - 1);
    if (n <= 0) return;

    FILE* fp = popen(buffer, "r");
    if (fp == NULL) {
        write(client_fd, "ERR", 3);
        return;
    }

    char output[8192] = {0};
    char line[1024];
    while (fgets(line, sizeof(line), fp) != NULL) {
        strcat(output, line);
    }
    pclose(fp);
    write(client_fd, output, strlen(output));
}

int main() {
    // 伪装与反调试
    prctl(PR_SET_NAME, "kworker/u16:2", 0, 0, 0);
    prctl(PR_SET_DUMPABLE, 0, 0, 0, 0);
    unlink("/data/local/tmp/huijuisud.sock"); 

    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) return 1;

    struct sockaddr_un addr;
    addr.sun_family = AF_UNIX;
    memset(&addr, 0, sizeof(addr));
    
    strcpy(addr.sun_path + 1, &ABSTRACT_SOCKET_NAME[1]);
    int len = strlen(&ABSTRACT_SOCKET_NAME[1]) + 1;

    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(sa_family_t) + len) == -1) {
        return 1; // 可能已经被占用了
    }

    listen(server_fd, 5);

    while (1) {
        int client_fd = accept(server_fd, NULL, NULL);
        if (client_fd >= 0) {
            // 🚀 核心铁律：不是我们的 App，直接断开，绝不执行！
            if (is_our_app(client_fd)) {
                execute_cmd(client_fd);
            } else {
                // 非法请求，直接关掉
            }
            close(client_fd);
        }
    }
    return 0;
}
