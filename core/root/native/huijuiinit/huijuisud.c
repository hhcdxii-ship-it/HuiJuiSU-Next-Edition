#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>

#define ABSTRACT_SOCKET_NAME "\0huijui_ghost_socket"
#define APP_PACKAGE_NAME "com.huijui.sunext.edition"

/* 单次请求的读超时：防止客户端连上却不发数据，把整个守护进程卡死 */
#define CLIENT_RECV_TIMEOUT_SEC 5
#define CLIENT_SEND_TIMEOUT_SEC 5
#define CMD_BUF_SIZE 4096
#define OUT_BUF_SIZE 8192

/*
 * 这个守护进程本身**空闲时零 CPU**（阻塞在 accept 上），功耗是好的。
 * 但旧实现有三个会被误判成"卡顿/发热"的问题：
 *
 *  1. 🔴 没有 SO_RCVTIMEO。客户端连上来不发数据，`read()` 会永久阻塞，
 *     而这是单线程 accept 循环 —— 整个守护进程从此不再接受任何连接，
 *     管理器里所有 Root 操作开始转圈。现在设 5 秒读/写超时。
 *
 *  2. 🔴 `strcat(output, line)` 往 8KB 栈缓冲里无界拼接。一条输出超过 8KB
 *     的命令（比如 `ls -R /system`）直接**栈溢出**，进程被内核打死。
 *     现在按剩余空间截断并补一个标记。
 *
 *  3. 没有处理 SIGPIPE：客户端提前断开时 write() 会触发 SIGPIPE，默认动作是
 *     终止进程 —— 守护进程自杀。现在忽略它，并在写失败时安静收尾。
 */

static int is_our_app(int client_fd)
{
	struct ucred cred;
	socklen_t len = sizeof(struct ucred);

	if (getsockopt(client_fd, SOL_SOCKET, SO_PEERCRED, &cred, &len) == -1)
		return 0;

	/* root 与 system 直接放行（adb shell 等） */
	if (cred.uid == 0 || cred.uid == 1000)
		return 1;

	{
		char path[64];
		char cmdline[256] = { 0 };
		int fd;

		snprintf(path, sizeof(path), "/proc/%d/cmdline", cred.pid);
		fd = open(path, O_RDONLY);
		if (fd >= 0) {
			ssize_t n = read(fd, cmdline, sizeof(cmdline) - 1);
			close(fd);
			if (n > 0 && strstr(cmdline, APP_PACKAGE_NAME) != NULL)
				return 1;
		}
	}
	return 0;
}

static void write_all(int fd, const char *buf, size_t len)
{
	size_t off = 0;

	while (off < len) {
		ssize_t n = write(fd, buf + off, len - off);
		if (n <= 0) {
			if (errno == EINTR)
				continue;
			break;
		}
		off += (size_t)n;
	}
}

static void execute_cmd(int client_fd)
{
	char buffer[CMD_BUF_SIZE];
	char output[OUT_BUF_SIZE];
	size_t used = 0;
	size_t total_read = 0;
	ssize_t n;
	char line[1024];
	FILE *fp;

	memset(buffer, 0, sizeof(buffer));

	/* 命令可能分包到达，读到换行或缓冲满为止 */
	while (total_read < sizeof(buffer) - 1) {
		n = read(client_fd, buffer + total_read, sizeof(buffer) - 1 - total_read);
		if (n <= 0)
			break;
		total_read += (size_t)n;
		if (memchr(buffer, '\n', total_read) != NULL)
			break;
	}
	if (total_read == 0)
		return;

	buffer[total_read] = '\0';

	fp = popen(buffer, "r");
	if (fp == NULL) {
		write_all(client_fd, "ERR", 3);
		return;
	}

	while (fgets(line, sizeof(line), fp) != NULL) {
		size_t need = strlen(line);

		if (used + need + 64 >= sizeof(output)) {
			const char *mark = "\n...[truncated]\n";
			size_t mark_len = strlen(mark);

			if (used + mark_len < sizeof(output)) {
				memcpy(output + used, mark, mark_len);
				used += mark_len;
			}
			/* 继续排空管道，避免子进程因管道写满而阻塞 */
			while (fgets(line, sizeof(line), fp) != NULL)
				;
			break;
		}
		memcpy(output + used, line, need);
		used += need;
	}
	pclose(fp);

	if (used == 0)
		write_all(client_fd, "(no output)", 11);
	else
		write_all(client_fd, output, used);
}

int main(void)
{
	struct sockaddr_un addr;
	struct timeval tv;
	int server_fd;

	signal(SIGPIPE, SIG_IGN);

	prctl(PR_SET_NAME, "kworker/u16:2", 0, 0, 0);
	prctl(PR_SET_DUMPABLE, 0, 0, 0);
	unlink("/data/local/tmp/huijuisud.sock");

	server_fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
	if (server_fd < 0)
		return 1;

	memset(&addr, 0, sizeof(addr));
	addr.sun_family = AF_UNIX;
	strcpy(addr.sun_path + 1, &ABSTRACT_SOCKET_NAME[1]);

	{
		int len = (int)strlen(&ABSTRACT_SOCKET_NAME[1]) + 1;
		if (bind(server_fd, (struct sockaddr *)&addr,
			 sizeof(sa_family_t) + (size_t)len) == -1) {
			close(server_fd);
			return 1; /* 可能已有实例在跑 */
		}
	}

	listen(server_fd, 5);

	tv.tv_sec = CLIENT_RECV_TIMEOUT_SEC;
	tv.tv_usec = 0;

	for (;;) {
		int client_fd = accept4(server_fd, NULL, NULL, SOCK_CLOEXEC);

		if (client_fd < 0) {
			if (errno == EINTR || errno == ECONNABORTED)
				continue;
			usleep(50 * 1000); /* 防止 accept 出错时空转烧 CPU */
			continue;
		}

		setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
		setsockopt(client_fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

		if (is_our_app(client_fd))
			execute_cmd(client_fd);

		close(client_fd);
	}
}
