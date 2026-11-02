# 关闭 KCFI，防止内核校验不通过直接重启。
#
# ⚠️ -fno-sanitize=kcfi 是 clang 16+ 才支持的参数。
#    5.10/5.15 的 GKI 必须用 clang-14 编译，而 clang-14 遇到这个参数会直接报
#    "unsupported argument 'kcfi' to option 'fno-sanitize='" 并中止。
#    用 kbuild 的 cc-option 探测一下，编译器支持才加。
ifneq ($(call cc-option,-fno-sanitize=kcfi,),)
ccflags-y += -fno-sanitize=kcfi
endif

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
