#pragma once
#include <cstdlib>
#include <cstddef>
static inline void* malloc(size_t size) { void* p = malloc(size); if (!p) abort(); return p; }
static inline size_t align_to(size_t v, size_t a) { return (v + a - 1) & ~(a - 1); }
