#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>      // 🚀 补上 open 的头文件
#include <sys/stat.h>   // 🚀 补上 chmod 的头文件

#define SOCKET_PATH "/dev/socket/huijuisud"

void klog(const char *msg) {
    int fd = open("/dev/kmsg", O_WRONLY);
    if (fd >= 0) { write(fd, msg, strlen(msg)); close(fd); }
}

int main() {
    klog("huijuisud: Starting daemon...\n");
    
    // 创建 Socket
    unlink(SOCKET_PATH);
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    struct sockaddr_un addr;
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path, SOCKET_PATH);
    
    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        klog("huijuisud: bind failed\n");
        return 1;
    }
    
    chmod(SOCKET_PATH, 0666);
    listen(server_fd, 5);
    klog("huijuisud: Listening on socket\n");

    while (1) {
        int client_fd = accept(server_fd, NULL, NULL);
        if (client_fd >= 0) {
            // 极度简化的逻辑：收到请求，直接放行（实际需要App弹窗鉴权）
            klog("huijuisud: Got a su request, granting root...\n");
            write(client_fd, "OK", 2);
            // 这里实际上需要 fork 进程并执行 shell，暂时只做握手
            close(client_fd);
        }
    }
    return 0;
}
