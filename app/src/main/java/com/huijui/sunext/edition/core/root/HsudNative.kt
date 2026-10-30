package com.huijui.sunext.edition.core.root

object HsudNative {
    init {
        System.loadLibrary("hsud")
    }
    external fun runCommand(workDir: String?, vararg args: String): Int
}
