#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>

#define SOCKET_PATH "/data/adb/huijuisu/huijui.sock"
#define ACTIVE_FILE "/data/adb/huijuisu/.active"
#define PID_FILE    "/data/adb/huijuisu/.pid"
#define VERSION_FILE "/data/adb/huijuisu/version"
#define DIR_PATH    "/data/adb/huijuisu"

// 清理函数，退出时删掉标记
void cleanup(int sig) {
    remove(ACTIVE_FILE);
    remove(PID_FILE);
    remove(SOCKET_PATH);
    exit(0);
}

// 执行命令并将结果写回 socket
void execute_command(int client_fd, const char *cmd) {
    char buffer[4096];
    FILE *fp;
    
    // 我们用 popen 执行命令，这比 fork/exec 简单且够用
    // 注意：这里的 popen 默认走的是 /bin/sh
    fp = popen(cmd, "r");
    if (fp == NULL) {
        const char *err = "ERR: popen failed\n-1\n";
        write(client_fd, err, strlen(err));
        return;
    }

    // 读取命令输出
    while (fgets(buffer, sizeof(buffer), fp) != NULL) {
        write(client_fd, buffer, strlen(buffer));
    }

    // 获取退出码
    int status = pclose(fp);
    int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;

    // 发送结束标记和退出码
    char end_marker[64];
    snprintf(end_marker, sizeof(end_marker), "__HIJ_SOCKET_END__\n%d\n", exit_code);
    write(client_fd, end_marker, strlen(end_marker));
}

int main(int argc, char *argv[]) {
    // 1. 注册信号，清理环境
    signal(SIGINT, cleanup);
    signal(SIGTERM, cleanup);

    // 2. 创建目录
    mkdir(DIR_PATH, 0755);
    
    // 3. 写 PID 和版本文件
    FILE *f = fopen(PID_FILE, "w");
    if (f) { fprintf(f, "%d\n", getpid()); fclose(f); }
    
    f = fopen(VERSION_FILE, "w");
    if (f) { fprintf(f, "vNext-Edition (C-Core Daemon)\n"); fclose(f); }

    // 4. 创建激活标记（验证器查这个）
    f = fopen(ACTIVE_FILE, "w");
    if (f) { fprintf(f, "active\n"); fclose(f); }

    // 5. 初始化 Unix Socket
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd == -1) {
        perror("socket");
        cleanup(0);
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, SOCKET_PATH, sizeof(addr.sun_path) - 1);
    unlink(SOCKET_PATH); // 删掉可能存在的旧 socket

    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        perror("bind");
        cleanup(0);
    }

    // 设置权限，让 App 能连
    chmod(SOCKET_PATH, 0666);

    // 监听，队列长度 5
    if (listen(server_fd, 5) == -1) {
        perror("listen");
        cleanup(0);
    }

    // 6. 主循环，接收客户端连接
    while (1) {
        int client_fd = accept(server_fd, NULL, NULL);
        if (client_fd < 0) continue;

        char cmd_buffer[2048] = {0};
        ssize_t bytes_read = read(client_fd, cmd_buffer, sizeof(cmd_buffer) - 1);
        
        if (bytes_read > 0) {
            // 如果命令以 EXEC 开头，提取后面的部分
            if (strncmp(cmd_buffer, "EXEC ", 5) == 0) {
                char *cmd = cmd_buffer + 5;
                // 去掉可能存在的换行符
                cmd[strcspn(cmd, "\r\n")] = 0;
                execute_command(client_fd, cmd);
            } else if (strncmp(cmd_buffer, "PING", 4) == 0) {
                const char *pong = "PONG\n";
                write(client_fd, pong, strlen(pong));
            }
        }
        close(client_fd);
    }

    return 0;
}
