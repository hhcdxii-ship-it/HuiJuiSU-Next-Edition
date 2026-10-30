#include "base.hpp"

void hsud_report_result(int code) {
    if (code == 0) {
        LOGI("result=ok\n");
    } else {
        LOGE("result=err code=%d\n", code);
    }
}
