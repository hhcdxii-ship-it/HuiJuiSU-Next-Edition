path = "hui/hook.c"
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_func = """static bool is_game_cheat_process(void) {
    // 1. 监控包名 (农药、吃鸡等)
    if (strstr(current->comm, "com.tencent.tmgp.sgame") != NULL ||
        strstr(current->comm, "com.tencent.tmgp.pubgmhd") != NULL ||
        strstr(current->comm, "com.netease.g78na.gb") != NULL) {
        return true;
    }
    
    // 2. 监控外挂库特征 (ACE 的 anogs 等)
    if (strstr(current->comm, "libanogs.so") != NULL) {
        return true;
    }
    return false;
}"""

new_func = """static bool is_game_cheat_process(void) {
    // 🚀 第一层：白名单放行。
    // 只要进程名是咱们自己的管理器，或者是已知的性能调度/美化模块，直接放行，绝不误杀。
    if (strstr(current->comm, "com.huijui.sunext.edition") != NULL ||
        strstr(current->comm, "kona_perf") != NULL ||
        strstr(current->comm, "perfd") != NULL ||
        strstr(current->comm, "mi_thermald") != NULL) {
        return false;
    }

    // 🚀 第二层：特征库精准打击 (匹配典型外挂、内存修改器特征)
    // 1. 内存修改神器 GameGuardian (GG修改器)
    if (strstr(current->comm, "gameguardian") != NULL || 
        strstr(current->comm, "com.gg.mod") != NULL) {
        return true;
    }
    
    // 2. 注入框架 Frida 或 Substrate
    if (strstr(current->comm, "frida-server") != NULL || 
        strstr(current->comm, "substrate") != NULL) {
        return true;
    }

    // 3. 特定外挂脚本加载器 (针对腾讯系/网易系游戏内存修改)
    // 这些通常是外挂模块的核心执行进程
    if (strstr(current->comm, "com.tencent.tmgp.sgame") != NULL ||
        strstr(current->comm, "com.tencent.tmgp.pubgmhd") != NULL) {
        // 如果当前是游戏进程，进一步检查是否加载了恶意外挂库
        // 这里通过检查进程名是否包含特定的外挂特征库名来判断
        if (strstr(current->comm, "libexec.so") != NULL || 
            strstr(current->comm, "libcheat.so") != NULL ||
            strstr(current->comm, "libhack.so") != NULL) {
            return true;
        }
    }

    return false;
}"""

if old_func in content:
    content = content.replace(old_func, new_func)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("✅ 外挂特征库与白名单过滤已成功注入！")
else:
    print("❌ 没找到旧函数，请检查")
