package com.huijui.sunext.edition.core.utils

import android.os.Build
import android.text.TextUtils
import java.lang.reflect.Method

object RomUtils {

    private fun getProp(key: String, defaultValue: String = ""): String {
        return try {
            val spClz = Class.forName("android.os.SystemProperties")
            val method: Method = spClz.getDeclaredMethod("get", String::class.java)
            val value = method.invoke(spClz, key) as String
            if (TextUtils.isEmpty(value)) defaultValue else value
        } catch (e: Throwable) {
            defaultValue
        }
    }

    fun isXiaomi(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("xiaomi") || brand.contains("redmi") || 
               manufacturer.contains("xiaomi") || 
               !TextUtils.isEmpty(getProp("ro.miui.ui.version.name")) ||
               !TextUtils.isEmpty(getProp("ro.mi.os.version.name"))
    }

    fun getMiuiVersion(): String = getProp("ro.miui.ui.version.name")
    fun getHyperOsVersion(): String = getProp("ro.mi.os.version.name")

    fun isHyperOs(): Boolean {
        if (!TextUtils.isEmpty(getHyperOsVersion())) return true
        return isXiaomi() && getMiuiVersion().startsWith("V816") // 部分旧版 HyperOS 属性遗留
    }

    fun isMiui(): Boolean {
        return !isHyperOs() && !TextUtils.isEmpty(getMiuiVersion())
    }
    
    // 🚀 提取大版本号，用于界面展示 (如 OS1, OS2)
    fun getOsMajorVersion(): String {
        if (isHyperOs()) {
            val version = getHyperOsVersion()
            if (version.startsWith("OS")) {
                return version.substringBefore(".") // e.g., "OS1"
            }
            return "HyperOS"
        } else if (isMiui()) {
            return "MIUI"
        }
        return "Android"
    }
}
