obj-m += HUI.o

HUI-y := hui_main.o \
	 hui/hook.o \
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
	 hui/net_guard.o \
	 hui/overlay_engine.o \
	 hui/mount_guard.o \
	 hui/hui_config.o

KDIR ?= /lib/modules/$(shell uname -r)/build
PWD := $(shell pwd)

all:
	$(MAKE) -C $(KDIR) M=$(PWD) modules

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
