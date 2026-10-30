#pragma once

#include <cstdio>
#include <cstdarg>
#include <cstring>
#include <cstdlib>
#include <cerrno>
#include <cstdint>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <dirent.h>
#include <memory>
#include <string>
#include <string_view>
#include <functional>
#include <utility>
#include <initializer_list>
#include <type_traits>

#define __printflike(x, y) __attribute__((format(printf, x, y)))

/* ===== xwrap ===== */
ssize_t xwrite(int fd, const void *buf, size_t count);
int xopen(const char *pathname, int flags);
int xopen(const char *pathname, int flags, mode_t mode);
FILE *xfopen(const char *pathname, const char *mode);
FILE *xfdopen(int fd, const char *mode);
void *xmalloc(size_t size);
void *xcalloc(size_t nmemb, size_t size);
void *xrealloc(void *ptr, size_t size);
void *xmmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset);
ssize_t xsendfile(int out_fd, int in_fd, off_t *offset, size_t count);

/* ===== logging ===== */
void LOGD(const char *fmt, ...) __printflike(1, 2);
void LOGI(const char *fmt, ...) __printflike(1, 2);
void LOGW(const char *fmt, ...) __printflike(1, 2);
void LOGE(const char *fmt, ...) __printflike(1, 2);
#define PLOGE(fmt, args...) LOGE(fmt " failed with %d: %s\n", ##args, errno, std::strerror(errno))
void cmdline_logging();

/* ===== misc ===== */
uint32_t binary_gcd(uint32_t u, uint32_t v);

static inline bool str_starts(std::string_view str, std::string_view prefix) {
    return str.size() >= prefix.size() && str.compare(0, prefix.size(), prefix) == 0;
}
static inline bool str_ends(std::string_view str, std::string_view suffix) {
    return str.size() >= suffix.size() &&
           str.compare(str.size() - suffix.size(), suffix.size(), suffix) == 0;
}
static inline bool str_contains(std::string_view str, std::string_view sub) {
    return str.find(sub) != std::string_view::npos;
}

/* ===== align ===== */
template <typename T>
static inline T align_to(T v, int a) {
    static_assert(std::is_integral<T>::value, "T must be integral");
    return (v + a - 1) / a * a;
}
template <typename T>
static inline T align_padding(T v, int a) {
    return align_to(v, a) - v;
}

/* ===== files ===== */
using sFILE = std::unique_ptr<FILE, decltype(&fclose)>;
using sDIR = std::unique_ptr<DIR, decltype(&closedir)>;

sDIR make_dir(DIR *dp);
sFILE make_file(FILE *fp);

static inline sFILE open_file(const char *path, const char *mode) {
    return make_file(fopen(path, mode));
}
static inline sFILE xopen_file(const char *path, const char *mode) {
    return make_file(xfopen(path, mode));
}
static inline sFILE xopen_file(int fd, const char *mode) {
    return make_file(xfdopen(fd, mode));
}
static inline sDIR open_dir(const char *path) {
    return make_dir(opendir(path));
}

struct file_attr {
    struct stat st;
    char con[128];
};

struct byte_data {
    using str_pairs = std::initializer_list<std::pair<std::string_view, std::string_view>>;
    uint8_t *buf = nullptr;
    size_t sz = 0;

    int patch(str_pairs list) { return patch(true, list); }
    int patch(bool log, str_pairs list) { (void)log; (void)list; return 0; }
    bool contains(std::string_view pattern, bool log = true) const { (void)pattern; (void)log; return false; }
protected:
    void swap(byte_data &o) {
        std::swap(buf, o.buf);
        std::swap(sz, o.sz);
    }
};

struct mmap_data : public byte_data {
    mmap_data() = default;
    mmap_data(const mmap_data&) = delete;
    mmap_data(mmap_data &&o) { swap(o); }
    mmap_data(const char *name, bool rw = false);
    ~mmap_data() { if (buf) munmap(buf, sz); }
    mmap_data& operator=(mmap_data &&other) { swap(other); return *this; }
};

int mkdirs(std::string path, mode_t mode);
void rm_rf(const char *path);
void mv_path(const char *src, const char *dest);
void full_read(int fd, std::string &str);
void full_read(const char *filename, std::string &str);
std::string full_read(int fd);
std::string full_read(const char *filename);
void write_zero(int fd, size_t size);
void file_readline(bool trim, FILE *fp, const std::function<bool(std::string_view)> &fn);
void file_readline(bool trim, const char *file, const std::function<bool(std::string_view)> &fn);
void file_readline(const char *file, const std::function<bool(std::string_view)> &fn);
void parse_prop_file(FILE *fp, const std::function<bool(std::string_view, std::string_view)> &fn);
void parse_prop_file(const char *file, const std::function<bool(std::string_view, std::string_view)> &fn);
void frm_rf(int dirfd);
void clone_dir(int src, int dest);

/* ===== crc32_z compatibility for Android NDK ===== */
#include <zlib.h>
#ifndef crc32_z
static inline uLong crc32_z(uLong crc, const Bytef *buf, z_size_t len) {
    return crc32(crc, buf, (uInt)len);
}
#endif
