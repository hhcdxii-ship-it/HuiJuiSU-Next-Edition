cmake_minimum_required(VERSION 3.18)
project(hsud CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIE -fPIC -Os -s -Wno-everything")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIE -fPIC -Os -s -w")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -pie")

# ===== 只把 xz 当子项目；zopfli 直接编 .c（排除自带命令行入口 zopfli_bin.c）=====
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(external/xz EXCLUDE_FROM_ALL)

file(GLOB BOOT_SRC   ${CMAKE_CURRENT_SOURCE_DIR}/boot/*.cpp)
file(GLOB SHIM_SRC   ${CMAKE_CURRENT_SOURCE_DIR}/shim/*.cpp)
file(GLOB ZOPFLI_SRC ${CMAKE_CURRENT_SOURCE_DIR}/external/zopfli/src/zopfli/*.c)
list(FILTER ZOPFLI_SRC EXCLUDE REGEX "zopfli_bin\\.c$")

add_executable(hsud
    ${BOOT_SRC}
    ${SHIM_SRC}
    ${ZOPFLI_SRC}
    external/mincrypt/sha.c
    external/mincrypt/sha256.c
    external/lz4/lib/lz4.c
    external/lz4/lib/lz4hc.c
    external/lz4/lib/lz4frame.c
    external/lz4/lib/xxhash.c
    external/bzip2/blocksort.c
    external/bzip2/bzlib.c
    external/bzip2/compress.c
    external/bzip2/crctable.c
    external/bzip2/decompress.c
    external/bzip2/huffman.c
    external/bzip2/randtable.c
    external/dtc/libfdt/fdt.c
    external/dtc/libfdt/fdt_ro.c
    external/dtc/libfdt/fdt_rw.c
    external/dtc/libfdt/fdt_strerror.c
    external/dtc/libfdt/fdt_sw.c
    external/dtc/libfdt/fdt_wip.c
    external/dtc/libfdt/fdt_empty_tree.c
    external/dtc/libfdt/fdt_addresses.c
    external/dtc/libfdt/fdt_overlay.c
)

target_include_directories(hsud PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/shim
    ${CMAKE_CURRENT_SOURCE_DIR}/boot
    ${CMAKE_CURRENT_SOURCE_DIR}/external/mincrypt/include
    ${CMAKE_CURRENT_SOURCE_DIR}/external/lz4/lib
    ${CMAKE_CURRENT_SOURCE_DIR}/external/bzip2
    ${CMAKE_CURRENT_SOURCE_DIR}/external/dtc/libfdt
    ${CMAKE_CURRENT_SOURCE_DIR}/external/xz/src/liblzma/api
    ${CMAKE_CURRENT_SOURCE_DIR}/external/zopfli/src
)

target_link_libraries(hsud z log liblzma)

# ===== huijuiinit 静态劫持引擎 =====
add_executable(huijuiinit
    huijuiinit/huijuiinit.c
)
# 🚀 强制静态链接，不依赖 Android 的 linker
target_link_options(huijuiinit PRIVATE -static -Os -s)
set_target_properties(huijuiinit PROPERTIES
    POSITION_INDEPENDENT_CODE OFF
)
