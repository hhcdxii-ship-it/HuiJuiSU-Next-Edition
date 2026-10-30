#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>

#define SOCKET_PATH "/dev/socket/huijuisud"

int main(int argc, char **argv) {
    int client_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    struct sockaddr_un addr;
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path, SOCKET_PATH);
    
    if (connect(client_fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        fprintf(stderr, "su: cannot connect to huijuisud\n");
        return 1;
    }
    
    char buf[2];
    recv(client_fd, buf, 2, 0);
    close(client_fd);
    
    if (buf[0] == 'O' && buf[1] == 'K') {
        // 鉴权通过，设置为 root 并执行 shell
        setuid(0);
        setgid(0);
        if (argc > 1) {
            execvp(argv[1], &argv[1]);
        } else {
            execl("/system/bin/sh", "sh", NULL);
        }
    }
    return 0;
}
