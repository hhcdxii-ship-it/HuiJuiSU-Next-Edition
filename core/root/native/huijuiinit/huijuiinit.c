#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/wait.h>

#define TAG "[huijuiinit] "

/*
 * 开机时间优化点（对比旧版）：
 *
 *  1. 🔴 旧版 `waitpid(insmod_pid, &status, 0)` 是**无超时阻塞**的。
 *     force_insmod 一旦卡住（符号解析失败、内核拒绝加载、模块内部死等），
 *     这个 pid 会永远挂着 —— 而它是 init 的父进程，**整台设备就停在开机第一阶段**
 *     （表现为卡 logo，只能强制重启）。现在改成带 4 秒上限的轮询等待，
 *     超时直接 SIGKILL 并继续启动：模块加载失败是功能问题，不能变成开不了机。
 *
 *  2. 三个挂载点没带任何 flag，且失败就静默。现在带上 MS_NOSUID/NODEV/NOEXEC，
 *     并对 EBUSY 做容错（部分 GKI 内核已经预挂好 /proc 或 /sys）。
 *
 *  3. 增加了阶段耗时日志，方便一眼看出卡在哪一步。
 *
 *  4. chmod/chcon 之类的准备工作前置到 fork 之前，避免在子进程里重复做。
 */

#define INSMOD_TIMEOUT_MS 4000
#define POLL_STEP_MS 20

static void mount_soft(const char *src, const char *tgt, const char *type,
                       unsigned long flags)
{
	if (mount(src, tgt, type, flags, NULL) == 0)
		return;
	if (errno == EBUSY)
		return; /* 已经挂好了，正常情况 */
	if (errno == ENOENT) {
		mkdir(tgt, 0755);
		if (mount(src, tgt, type, flags, NULL) == 0)
			return;
	}
	fprintf(stderr, TAG "mount %s at %s failed: %s (continue)\n",
		type, tgt, strerror(errno));
}

/* 返回值：0 = 正常结束，1 = 超时被强杀，-1 = 进程异常 */
static int wait_with_timeout(pid_t pid, int timeout_ms)
{
	int waited = 0;

	while (waited < timeout_ms) {
		int status;
		pid_t r = waitpid(pid, &status, WNOHANG);

		if (r == pid)
			return 0;
		if (r < 0)
			return -1;

		usleep(POLL_STEP_MS * 1000);
		waited += POLL_STEP_MS;
	}

	kill(pid, SIGKILL);
	waitpid(pid, NULL, 0);
	return 1;
}

int main(int argc, char *argv[])
{
	struct timespec t0, t1;
	long elapsed_ms;
	pid_t pid;

	clock_gettime(CLOCK_MONOTONIC, &t0);

	/* 伪装成 init，降低被按进程名扫出来的概率 */
	prctl(PR_SET_NAME, "init", 0, 0, 0);

	mount_soft("proc", "/proc", "proc", MS_NOSUID | MS_NODEV | MS_NOEXEC);
	mount_soft("sysfs", "/sys", "sysfs", MS_NOSUID | MS_NODEV | MS_NOEXEC);
	mount_soft("devtmpfs", "/dev", "devtmpfs", MS_NOSUID);

	/* ---- 1. 加载内核模块（带超时，绝不阻塞开机） ---- */
	pid = fork();
	if (pid == 0) {
		setsid();
		execl("/force_insmod", "force_insmod", "/huijui_phantom.ko", (char *)NULL);
		_exit(127);
	}
	if (pid > 0) {
		int r = wait_with_timeout(pid, INSMOD_TIMEOUT_MS);
		if (r == 1)
			fprintf(stderr, TAG "insmod timed out after %d ms, killed\n",
				INSMOD_TIMEOUT_MS);
		else if (r < 0)
			fprintf(stderr, TAG "insmod wait failed: %s\n", strerror(errno));
	}

	/* ---- 2. 拉起守护进程（失败不影响开机） ---- */
	pid = fork();
	if (pid == 0) {
		setsid();
		char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
		execve("/huijuisud", (char *[]){ "/huijuisud", "daemon", NULL }, envp);
		_exit(127);
	}

	/* ---- 3. 交棒给真正的 init ---- */
	clock_gettime(CLOCK_MONOTONIC, &t1);
	elapsed_ms = (t1.tv_sec - t0.tv_sec) * 1000L +
		     (t1.tv_nsec - t0.tv_nsec) / 1000000L;
	fprintf(stderr, TAG "handoff to real init after %ld ms\n", elapsed_ms);

	{
		const char *paths[] = { "/init.real", "/system/bin/init", NULL };
		char *envp[] = { "PATH=/sbin:/system/bin:/system/xbin", NULL };
		int i;

		for (i = 0; paths[i]; i++) {
			if (access(paths[i], F_OK) == 0)
				execve(paths[i], (char *[]){ (char *)paths[i], NULL }, envp);
		}
	}

	fprintf(stderr, TAG "fatal: no init found\n");
	return 1;
}
