# 强制关闭 KCFI，防止内核校验不通过直接重启
ccflags-y += -fno-sanitize=kcfi

# 忽略警告
ccflags-y += -Wno-error

# ============================================================================
# 交付物只有两个模块：
#   huijui_phantom.ko —— 引擎本体，安装到 ramdisk，由 force_insmod 加载
#   temp_root.ko     —— 幽灵模式临时提权，独立于引擎、运行时单独加载
#
# 以前这里是 9 个 obj-m，每个 .c 各自 module_init，于是构建出一堆互不相干的
# 小模块；而安装链路只认单个 huijui_phantom.ko，对不上。
# 现在所有 huijui_* 子模块汇入 huijui_phantom。
# ============================================================================
obj-m += temp_root.o
obj-m += huijui_phantom.o

huijui_phantom-y := \
	huijui_main.o \
	huijui_init.o \
	huijui_hook.o \
	huijui_syscall.o \
	huijui_hide.o \
	huijui_guard.o \
	huijui_proc.o \
	huijui_mem.o \
	huijui_selinux.o
