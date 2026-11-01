#pragma once
#include <cstddef>

struct mmap_data {
    void* buf = nullptr;
    size_t sz = 0;
};
