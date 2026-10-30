obj-m += HuiJuiPhantom.o

HuiJuiPhantom-y := hui_main.o \
         hui/hook.o \
         hui/stealth.o \
         hui/kallsyms_resolver.o \
         hui/allowlist.o \
         hui/comm.o \
         hui/procfs.o \
         hui/hook_compat.o \
         hui/selinux_compat.o \
         hui/exec_compat.o \
         hui/import_compat.o \
         hui/env_compat.o \
         hui/perf_opt.o \
         hui/boot_opt.o \
         hui/game_opt.o \
         hui/protect.o \
         hui/guard_wipe.o \
         hui/guard_block.o \
         hui/flash_compat.o \
         hui/net_guard.o \
         hui/overlay_engine.o \
         hui/mount_guard.o \
         hui/hui_config.o \
         hui/temp_root.o \
         hui/protect_guard.o

# 🚀 体积与性能双重优化
ccflags-y += -Os -Wno-unused-function -Wno-unused-variable
ccflags-y += -fno-stack-protector
ccflags-y += -fno-asynchronous-unwind-tables -fno-unwind-tables
ccflags-y += -fno-pic

# 🚀 交叉编译核心设置
ARCH ?= arm64
CROSS_COMPILE ?= aarch64-linux-gnu-
# 优先使用预编译的 GKI 头文件目录
KDIR ?= /home/xiaomi/android-kernel/common

PWD := $(shell pwd)

all:
	$(MAKE) ARCH=$(ARCH) CC=clang LLVM=1 -C $(KDIR) M=$(PWD) modules KBUILD_MODPOST_WARN=1

clean:
	$(MAKE) ARCH=$(ARCH) CC=clang LLVM=1 -C $(KDIR) M=$(PWD) clean
