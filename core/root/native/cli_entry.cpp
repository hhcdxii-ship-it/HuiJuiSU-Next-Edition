#include <stdio.h>
#include <unistd.h>

extern "C" int hsud_main(int argc, char *argv[]);

int main(int argc, char *argv[]) {
    int rc = hsud_main(argc, argv);

    // ⚠️ 这里必须用 _exit 而不是 return。
    //
    // 实测在某些设备（Android 16/17 + KSU，开了 tagged_addr / PAC）上，
    // 进程正常退出时会崩在静态析构里：
    //   #00 abort  <-  #01 free+112  <-  #02 hsud_cli  <-  #03 __libc_init
    // 原因是全局/静态对象在析构时对**非 malloc 指针**调了 free()，
    // 而 bionic 会先检查指针标记，标记对不上就直接 abort
    // （报 "Pointer tag for 0x... was truncated"）。
    //
    // 对命令行工具来说，所有结果都已经写进文件了，退出时的析构没有意义，
    // 反而会把一次成功的解包变成 exit code 134。用 _exit 直接结束进程，
    // 不跑 atexit / 静态析构，从根上避开这类崩溃。
    fflush(nullptr);
    _exit(rc);
}
