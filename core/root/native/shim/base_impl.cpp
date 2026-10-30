#include "base.hpp"
#include <sys/mman.h>
#include <sys/sendfile.h>
#include <stdarg.h>

ssize_t xwrite(int fd, const void *buf, size_t count) {
    const char *p = (const char *)buf;
    size_t total = 0;
    while (total < count) {
        ssize_t n = write(fd, p + total, count - total);
        if (n < 0) { if (errno == EINTR) continue; return -1; }
        total += n;
    }
    return (ssize_t)total;
}

int xopen(const char *pathname, int flags) { return open(pathname, flags); }
int xopen(const char *pathname, int flags, mode_t mode) { return open(pathname, flags, mode); }

FILE *xfopen(const char *pathname, const char *mode) {
    FILE *f = fopen(pathname, mode);
    if (!f) abort();
    return f;
}
FILE *xfdopen(int fd, const char *mode) {
    FILE *f = fdopen(fd, mode);
    if (!f) abort();
    return f;
}

void *xmalloc(size_t size) {
    void *p = malloc(size);
    if (!p && size) abort();
    return p;
}
void *xcalloc(size_t nmemb, size_t size) {
    void *p = calloc(nmemb, size);
    if (!p && nmemb && size) abort();
    return p;
}
void *xrealloc(void *ptr, size_t size) {
    void *p = realloc(ptr, size);
    if (!p && size) abort();
    return p;
}
void *xmmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset) {
    void *p = mmap(addr, length, prot, flags, fd, offset);
    if (p == MAP_FAILED) abort();
    return p;
}
ssize_t xsendfile(int out_fd, int in_fd, off_t *offset, size_t count) {
    ssize_t ret = sendfile(out_fd, in_fd, offset, count);
    if (ret < 0) abort();
    return ret;
}

uint32_t binary_gcd(uint32_t u, uint32_t v) {
    if (u == 0) return v;
    if (v == 0) return u;
    auto shift = __builtin_ctz(u | v);
    u >>= __builtin_ctz(u);
    do {
        v >>= __builtin_ctz(v);
        if (u > v) { auto t = v; v = u; u = t; }
    } while ((v -= u));
    return u << shift;
}

sDIR make_dir(DIR *dp) { return sDIR(dp, closedir); }
sFILE make_file(FILE *fp) { return sFILE(fp, fclose); }

mmap_data::mmap_data(const char *name, bool rw) {
    int fd = xopen(name, rw ? O_RDWR : O_RDONLY);
    struct stat st;
    if (fstat(fd, &st) == 0) {
        sz = (size_t)st.st_size;
        buf = (uint8_t *)xmmap(nullptr, sz, rw ? (PROT_READ|PROT_WRITE) : PROT_READ,
                               MAP_PRIVATE, fd, 0);
    }
    close(fd);
}

static void hsud_log(const char *level, const char *fmt, va_list ap) {
    fprintf(stderr, "[HSUD:%s] ", level);
    vfprintf(stderr, fmt, ap);
    fflush(stderr);
}
void LOGD(const char *fmt, ...) { va_list ap; va_start(ap, fmt); hsud_log("debug", fmt, ap); va_end(ap); }
void LOGI(const char *fmt, ...) { va_list ap; va_start(ap, fmt); hsud_log("info", fmt, ap); va_end(ap); }
void LOGW(const char *fmt, ...) { va_list ap; va_start(ap, fmt); hsud_log("warn", fmt, ap); va_end(ap); }
void LOGE(const char *fmt, ...) { va_list ap; va_start(ap, fmt); hsud_log("error", fmt, ap); va_end(ap); }
void cmdline_logging() {}

void full_read(int fd, std::string &str) {
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) str.append(buf, n);
}
std::string full_read(int fd) { std::string s; full_read(fd, s); return s; }
void full_read(const char *filename, std::string &str) {
    int fd = xopen(filename, O_RDONLY);
    full_read(fd, str);
    close(fd);
}
std::string full_read(const char *filename) { std::string s; full_read(filename, s); return s; }

void write_zero(int fd, size_t size) {
    static const char zero[4096] = {0};
    while (size > 0) {
        size_t chunk = size < sizeof(zero) ? size : sizeof(zero);
        if (xwrite(fd, zero, chunk) < 0) break;
        size -= chunk;
    }
}

int mkdirs(std::string path, mode_t mode) {
    for (size_t i = 1; i < path.size(); ++i) {
        if (path[i] == '/') {
            path[i] = 0;
            mkdir(path.c_str(), mode);
            path[i] = '/';
        }
    }
    return mkdir(path.c_str(), mode);
}

void rm_rf(const char *path) { (void)path; }
void mv_path(const char *src, const char *dest) { rename(src, dest); }
void file_readline(bool, FILE *, const std::function<bool(std::string_view)> &) {}
void file_readline(bool, const char *, const std::function<bool(std::string_view)> &) {}
void file_readline(const char *, const std::function<bool(std::string_view)> &) {}
void parse_prop_file(FILE *, const std::function<bool(std::string_view, std::string_view)> &) {}
void parse_prop_file(const char *, const std::function<bool(std::string_view, std::string_view)> &) {}
void frm_rf(int) {}
void clone_dir(int, int) {}
