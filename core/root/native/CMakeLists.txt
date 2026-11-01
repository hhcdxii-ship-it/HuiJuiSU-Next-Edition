cmake_minimum_required(VERSION 3.18)
project(hsud CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ===== xz 子项目 =====
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(external/xz EXCLUDE_FROM_ALL)

# ===== 提取通用源文件 =====
file(GLOB BOOT_SRC   ${CMAKE_CURRENT_SOURCE_DIR}/boot/*.cpp)
file(GLOB ZOPFLI_SRC ${CMAKE_CURRENT_SOURCE_DIR}/external/zopfli/src/zopfli/*.c)
list(FILTER ZOPFLI_SRC EXCLUDE REGEX "zopfli_bin\\.c$")

set(COMMON_SRC
    ${BOOT_SRC}
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

set(COMMON_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/boot
    ${CMAKE_CURRENT_SOURCE_DIR}/external/mincrypt/include
    ${CMAKE_CURRENT_SOURCE_DIR}/external/lz4/lib
    ${CMAKE_CURRENT_SOURCE_DIR}/external/bzip2
    ${CMAKE_CURRENT_SOURCE_DIR}/external/dtc/libfdt
    ${CMAKE_CURRENT_SOURCE_DIR}/external/xz/src/liblzma/api
    ${CMAKE_CURRENT_SOURCE_DIR}/external/zopfli/src
)

# ==========================================
# 🚀 环境隔离编译
# ==========================================
if(ANDROID)
    # --- Android (NDK) 编译：生成 JNI 动态库 ---
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIE -fPIC -Os -s -Wno-everything")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIE -fPIC -Os -s -w")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -pie")

    file(GLOB SHIM_SRC ${CMAKE_CURRENT_SOURCE_DIR}/shim/*.cpp)
    # 排除 Linux 下不需要的 jni 头
    add_library(hsud SHARED ${COMMON_SRC} ${SHIM_SRC})
# 🚀 强制启用 16 KB 页面大小 ELF 对齐 (Android 15+ 兼容)
target_link_options(hsud PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

    target_include_directories(hsud PRIVATE ${COMMON_INCLUDES} ${CMAKE_CURRENT_SOURCE_DIR}/shim)
    target_link_libraries(hsud z log liblzma)

    # 守护进程
    add_executable(huijuisud huijuiinit/huijuisud.c)
    target_link_options(huijuisud PRIVATE -static -Os -s)

    # 劫持引擎
    add_executable(huijuiinit huijuiinit/huijuiinit.c)
    target_link_options(huijuiinit PRIVATE -static -Os -s)


    # 🚀 编译独立的命令行工具 hsud_cli (用于 App 内部调用)
    add_executable(hsud_cli ${COMMON_SRC}
        shim/base_impl.cpp
        shim/stream.cpp
        shim/info.cpp
        shim/result.cpp
        cli_entry.cpp
    )
    target_include_directories(hsud_cli PRIVATE ${COMMON_INCLUDES} ${CMAKE_CURRENT_SOURCE_DIR}/shim)
    target_link_libraries(hsud_cli z log liblzma)
    
    
    # 把编译好的 hsud_cli 拷贝到 assets 目录下
    add_custom_command(TARGET hsud_cli POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:hsud_cli> ${CMAKE_CURRENT_SOURCE_DIR}/../../assets/exploit/hsud_cli
    )

else()
    # --- Linux (WSL) 编译：生成独立可执行文件 hsud ---
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os -s -w -Wno-unused-result -fpermissive")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Os -s -w")

    # Linux 下需要排除 jni_wrapper.cpp，加上 base/stream 等 shim 文件
    add_executable(hsud_cli ${COMMON_SRC}
        shim/base_impl.cpp
        shim/stream.cpp
        shim/info.cpp
        shim/result.cpp
        cli_entry.cpp
    )
    set_target_properties(hsud_cli PROPERTIES OUTPUT_NAME "hsud")
    target_include_directories(hsud_cli PRIVATE ${COMMON_INCLUDES} ${CMAKE_CURRENT_SOURCE_DIR}/shim)
    target_link_libraries(hsud_cli z liblzma)
endif()
