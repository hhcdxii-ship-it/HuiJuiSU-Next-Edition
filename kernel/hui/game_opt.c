#include "hui.h"
#include <linux/cpufreq.h>
#include <linux/sched.h>

static bool __attribute__((unused)) hui_is_game_process(const char *comm) {
    if (strstr(comm, "tmgp") || strstr(comm, "game") || strstr(comm, "pubg") || strstr(comm, "mihoyo")) {
        return true;
    }
    return false;
}

static void __attribute__((unused)) hui_boost_game_performance(void) {
    if (hui_is_game_process(current->comm)) {
        set_user_nice(current, -20);
    }
}

static void __attribute__((unused)) hui_clean_game_env(void) {
}
