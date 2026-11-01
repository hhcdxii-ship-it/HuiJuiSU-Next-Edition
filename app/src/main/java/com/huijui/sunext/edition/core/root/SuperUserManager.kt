package com.huijui.sunext.edition.core.root

import android.content.Context
import java.io.File

object SuperUserManager {
    private const val PREFS_NAME = "huijui_superuser"
    private const val KEY_ALLOWLIST = "allowlist"
    
    // 必须与内核模块的宏定义完全一致
    const val CMD_GRANT_ROOT = 0x4801
    
    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun getAllowlist(): Set<String> {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs?.getStringSet(KEY_ALLOWLIST, emptySet()) ?: emptySet()
    }

    fun saveAllowlist(list: Set<String>) {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.edit()?.putStringSet(KEY_ALLOWLIST, list)?.apply()
    }

    /**
     * 🔥 闭环：向底层发送提权指令
     */
    fun grantRootToCurrentProcess(): Boolean {
        return sendCommandToKernel(CMD_GRANT_ROOT)
    }

    external fun sendCommandToKernel(commandCode: Int): Boolean
}
