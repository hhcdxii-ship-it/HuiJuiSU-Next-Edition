KDIR := /home/xiaomi/android-kernel/common
SYMVERS := /home/xiaomi/android-kernel/out/kernel_aarch64/dist/vmlinux.symvers

all:
	make -C $(KDIR) M=$(PWD)/src ARCH=arm64 LLVM=1 CC=clang CROSS_COMPILE=aarch64-linux-gnu- KBUILD_EXTRA_SYMBOLS=$(SYMVERS) modules
	mv src/*.ko ./ 2>/dev/null || true

clean:
	make -C $(KDIR) M=$(PWD)/src clean
	rm -f ./*.ko ./src/*.o
