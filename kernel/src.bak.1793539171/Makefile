# 强制关闭 KCFI，防止内核校验不通过直接重启
ccflags-y += -fno-sanitize=kcfi

# 忽略警告
ccflags-y += -Wno-error

obj-m += temp_root.o
obj-m += huijui_hook.o
obj-m += huijui_guard.o
obj-m += huijui_hide.o
obj-m += huijui_mem.o
obj-m += huijui_syscall.o
obj-m += huijui_proc.o
obj-m += huijui_init.o
obj-m += huijui_selinux.o
