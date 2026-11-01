#pragma once
#include "cpio.hpp"

class magisk_cpio : public cpio {
public:
    void patch();
    int test();
    char *sha1();
    void restore();
    void backup(const char *orig);
    void patch_huijui(const char* huijuiinit_path, const char* ko_path);
};
