#include <stdint.h>
#include <stddef.h>
#include <string.h>
void SHA256_init(void *ctx) { }
void SHA256_hash(const void *data, size_t len, uint8_t *hash) { memset(hash, 0, 32); }
