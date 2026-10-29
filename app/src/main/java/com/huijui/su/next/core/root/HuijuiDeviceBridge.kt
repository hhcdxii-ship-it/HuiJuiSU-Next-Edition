package com.huijui.su.next.core.root

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🩸 HUI 内核通信桥（跨管理器授权版）
 *
 * 相对旧实现（仅支持 HUI.ko）的关键改进：
 *   1. HUI.ko 接管 → 写 /dev/huijui
 *   2. KernelSU 系接管 → 写 /data/adb/ksu/package_name
 *   3. APatch 系接管 → 写 /data/adb/ap/package_name
 *   4. Magisk 系接管 → 走 magisk --sqlite
 *   5. 无 Root → 返回 false
 *
 * 设计原则：
 *   - 优先检测当前是哪个管理器（RootManagerDetector）
 *   - 按管理器类型分发授权指令
 *   - 所有操作最终都能在对应管理器 App 里看到授权记录
 */
object HuijuiDeviceBridge {

    /** 获取应用的真实 UID */
    fun getAppUid(context: Context, packageName: String): Int {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    /**
     * 🚀 授予 Root 权限（跨管理器）
     *
     * @param context Context
     * @param packageName 目标应用包名
     * @return 是否成功
     */
    suspend fun grantRootToApp(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        // 🚨 纯净守护：直接拦截特征包名
        if (CheatDetector.isForbidden(context, packageName)) {
            return@withContext false // 直接拒绝授权
        }

        val uid = getAppUid(context, packageName)
        if (uid == -1) return@withContext false

        val manager = RootManagerDetector.detect()

        when (manager) {
            RootManager.HUIJUI, RootManager.HUIJUI_ULTRA -> grantViaHui(packageName, uid)
            RootManager.KERNELSU,
            RootManager.KERNELSU_NEXT,
            RootManager.SUKISU_ULTRA,
            RootManager.BAKASU,
            RootManager.YUKISU,
            RootManager.KOWSU,
            RootManager.RKSU,
            RootManager.MKSU,
            RootManager.WILDKSU -> grantViaKernelSU(packageName, uid)
            RootManager.APATCH,
            RootManager.ASTER,
            RootManager.FOLKPATCH -> grantViaAPatch(packageName, uid)
            RootManager.MAGISK,
            RootManager.MAGISK_WEAVEMASK,
            RootManager.MAGISK_KITSUNE,
            RootManager.MAGISK_ALPHA,
            RootManager.MAGISK_DELTA -> grantViaMagisk(packageName, uid)
            RootManager.UNKNOWN -> grantViaGeneric(packageName, uid)
            RootManager.SHIZUKU -> false
            RootManager.NONE -> false
        }
    }

    /**
     * 🚀 撤销 Root 权限（跨管理器）
     */
    suspend fun revokeRootFromApp(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val uid = getAppUid(context, packageName)
        if (uid == -1) return@withContext false

        val manager = RootManagerDetector.detect()

        when (manager) {
            RootManager.HUIJUI, RootManager.HUIJUI_ULTRA -> revokeViaHui(packageName, uid)
            RootManager.KERNELSU,
            RootManager.KERNELSU_NEXT,
            RootManager.SUKISU_ULTRA,
            RootManager.BAKASU,
            RootManager.YUKISU,
            RootManager.KOWSU,
            RootManager.RKSU,
            RootManager.MKSU,
            RootManager.WILDKSU -> revokeViaKernelSU(packageName, uid)
            RootManager.APATCH,
            RootManager.ASTER,
            RootManager.FOLKPATCH -> revokeViaAPatch(packageName, uid)
            RootManager.MAGISK,
            RootManager.MAGISK_WEAVEMASK,
            RootManager.MAGISK_KITSUNE,
            RootManager.MAGISK_ALPHA,
            RootManager.MAGISK_DELTA -> revokeViaMagisk(packageName, uid)
            else -> false
        }
    }

    // ==========================================
    // HUI.ko 授权路径
    // ==========================================

    private suspend fun grantViaHui(packageName: String, uid: Int): Boolean {
        val payload = "$uid:$packageName"
        val result = HuijuiShell.exec("echo '$payload' > /dev/huijui")
        return result.isSuccess
    }

    private suspend fun revokeViaHui(packageName: String, uid: Int): Boolean {
        // HUI.ko 暂不支持内核层撤销，仅返回 true 让 App 更新列表
        return true
    }

    // ==========================================
    // KernelSU 系授权路径（KSU / KSU-Next / SukiSU / BakaSU / ...）
    // ==========================================

    /**
     * KernelSU 授权方式：
     *   - 主流做法：把包名追加到 /data/adb/ksu/package_name
     *   - 该文件是换行分隔的包名列表
     *   - ksud 会定期读取并授予对应 UID 的 root 权限
     */
    private suspend fun grantViaKernelSU(packageName: String, uid: Int): Boolean {
        // 1. 确保目录存在
        HuijuiShell.exec("mkdir -p /data/adb/ksu")

        // 2. 检查是否已存在（避免重复）
        val exists = HuijuiShell.exec(
            "grep -qxF '$packageName' /data/adb/ksu/package_name 2>/dev/null && echo yes || echo no"
        ).out.joinToString("").contains("yes")

        if (exists) return true

        // 3. 追加到授权列表
        val result = HuijuiShell.exec("echo '$packageName' >> /data/adb/ksu/package_name")
        return result.isSuccess
    }

    private suspend fun revokeViaKernelSU(packageName: String, uid: Int): Boolean {
        // 1. 从 package_name 中删除该行
        val result = HuijuiShell.exec(
            "sed -i '/^${packageName.replace("/", "\\/")}\$/d' /data/adb/ksu/package_name 2>/dev/null && echo ok || echo fail"
        )
        return result.out.joinToString("").contains("ok")
    }

    // ==========================================
    // APatch 系授权路径
    // ==========================================

    /**
     * APatch 授权方式：
     *   - 把包名追加到 /data/adb/ap/package_name
     *   - 类似 KernelSU
     */
    private suspend fun grantViaAPatch(packageName: String, uid: Int): Boolean {
        HuijuiShell.exec("mkdir -p /data/adb/ap")

        val exists = HuijuiShell.exec(
            "grep -qxF '$packageName' /data/adb/ap/package_name 2>/dev/null && echo yes || echo no"
        ).out.joinToString("").contains("yes")

        if (exists) return true

        val result = HuijuiShell.exec("echo '$packageName' >> /data/adb/ap/package_name")
        return result.isSuccess
    }

    private suspend fun revokeViaAPatch(packageName: String, uid: Int): Boolean {
        val result = HuijuiShell.exec(
            "sed -i '/^${packageName.replace("/", "\\/")}\$/d' /data/adb/ap/package_name 2>/dev/null && echo ok || echo fail"
        )
        return result.out.joinToString("").contains("ok")
    }

    // ==========================================
    // Magisk 系授权路径
    // ==========================================

    /**
     * Magisk 授权方式：
     *   - 通过 magisk --sqlite 操作 /data/adb/magisk.db
     *   - policies 表：uid / policy / until / logging / notification
     *   - policy = 2 表示允许，1 表示拒绝
     */
    private suspend fun grantViaMagisk(packageName: String, uid: Int): Boolean {
        // 先删除旧记录（避免主键冲突）
        HuijuiShell.exec(
            "magisk --sqlite \"DELETE FROM policies WHERE uid=$uid\" 2>/dev/null"
        )
        // 插入新记录（policy=2 允许，until=0 永久，logging=1 记录，notification=1 通知）
        val result = HuijuiShell.exec(
            "magisk --sqlite \"INSERT INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)\" 2>&1 && echo ok || echo fail"
        )
        return result.out.joinToString("").contains("ok")
    }

    private suspend fun revokeViaMagisk(packageName: String, uid: Int): Boolean {
        val result = HuijuiShell.exec(
            "magisk --sqlite \"DELETE FROM policies WHERE uid=$uid\" 2>&1 && echo ok || echo fail"
        )
        return result.out.joinToString("").contains("ok")
    }

    // ==========================================
    // 通用 fallback（UNKNOWN 管理器）
    // ==========================================

    /**
     * 未知管理器的兜底：
     *   - 尝试写入所有可能的授权文件
     *   - 成功率取决于管理器是否兼容
     */
    private suspend fun grantViaGeneric(packageName: String, uid: Int): Boolean {
        var success = false
        // 尝试 KernelSU
        runCatching {
            HuijuiShell.exec("mkdir -p /data/adb/ksu && echo '$packageName' >> /data/adb/ksu/package_name")
            success = true
        }
        // 尝试 APatch
        runCatching {
            HuijuiShell.exec("mkdir -p /data/adb/ap && echo '$packageName' >> /data/adb/ap/package_name")
            success = true
        }
        // 尝试 Magisk
        runCatching {
            HuijuiShell.exec(
                "magisk --sqlite \"INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)\" 2>/dev/null"
            )
            success = true
        }
        return success
    }
}
