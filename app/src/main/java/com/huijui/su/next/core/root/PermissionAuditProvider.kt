package com.huijui.su.next.core.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 权限审计提供者（纯 API 读取，最稳）
 *
 * 设计原则：
 *   - 只读 API，不 hook，不解析私有数据库，不执行 shell
 *   - 按"敏感权限"分组，用户一眼看清谁在用什么
 *   - 每个 App 标注"系统/用户" + "已授予/未授予"
 *   - 风险等级分三档（高 / 中 / 低），帮助用户判断优先级
 *
 * 敏感权限清单（14 个高频刚需权限）：
 *   相机 / 麦克风 / 位置 / 通讯录 / 短信 / 电话 / 存储 / 通话记录 /
 *   日历 / 身体传感器 / 通知 / 已安装应用列表 / 悬浮窗 / 无障碍
 */
object PermissionAuditProvider {

    enum class RiskLevel {
        HIGH,   // 相机 / 麦克风 / 位置 / 短信 / 电话
        MEDIUM, // 通讯录 / 存储 / 通话记录 / 日历
        LOW     // 通知 / 悬浮窗 / 身体传感器
    }

    data class SensitivePermission(
        val permission: String,
        val displayName: String,
        val description: String,
        val riskLevel: RiskLevel,
        val iconName: String
    )

    data class AppPermissionInfo(
        val packageName: String,
        val appName: String,
        val appInfo: ApplicationInfo,
        val isSystem: Boolean,
        val isGranted: Boolean
    )

    data class PermissionGroup(
        val permission: SensitivePermission,
        val apps: List<AppPermissionInfo>,
        val grantedCount: Int
    )

    data class AuditReport(
        val groups: List<PermissionGroup>,
        val totalApps: Int,
        val highRiskAppCount: Int
    )

    /**
     * 敏感权限清单（14 个）
     */
    private val SENSITIVE_PERMISSIONS = listOf(
        SensitivePermission(
            "android.permission.CAMERA", "相机",
            "可以随时打开摄像头拍照/录像", RiskLevel.HIGH, "camera"
        ),
        SensitivePermission(
            "android.permission.RECORD_AUDIO", "麦克风",
            "可以随时录制声音", RiskLevel.HIGH, "mic"
        ),
        SensitivePermission(
            "android.permission.ACCESS_FINE_LOCATION", "精确位置",
            "能持续追踪你的精确位置", RiskLevel.HIGH, "location"
        ),
        SensitivePermission(
            "android.permission.ACCESS_COARSE_LOCATION", "粗略位置",
            "能追踪你所在的城市/区域", RiskLevel.HIGH, "location"
        ),
        SensitivePermission(
            "android.permission.READ_SMS", "读取短信",
            "能看到你的所有短信内容", RiskLevel.HIGH, "sms"
        ),
        SensitivePermission(
            "android.permission.SEND_SMS", "发送短信",
            "能自动发送短信（含扣费短信）", RiskLevel.HIGH, "sms"
        ),
        SensitivePermission(
            "android.permission.CALL_PHONE", "拨打电话",
            "能自动拨打电话", RiskLevel.HIGH, "phone"
        ),
        SensitivePermission(
            "android.permission.READ_PHONE_STATE", "读取电话状态",
            "能读取 IMEI / 手机号等设备标识", RiskLevel.HIGH, "phone"
        ),
        SensitivePermission(
            "android.permission.READ_CONTACTS", "读取通讯录",
            "能看到你的所有联系人", RiskLevel.MEDIUM, "contacts"
        ),
        SensitivePermission(
            "android.permission.READ_CALL_LOG", "读取通话记录",
            "能看到你打过的所有电话", RiskLevel.MEDIUM, "call_log"
        ),
        SensitivePermission(
            "android.permission.READ_EXTERNAL_STORAGE", "读取存储",
            "能读取你的所有文件", RiskLevel.MEDIUM, "storage"
        ),
        SensitivePermission(
            "android.permission.WRITE_EXTERNAL_STORAGE", "写入存储",
            "能修改/删除你的文件", RiskLevel.MEDIUM, "storage"
        ),
        SensitivePermission(
            "android.permission.READ_CALENDAR", "读取日历",
            "能看到你的日程安排", RiskLevel.MEDIUM, "calendar"
        ),
        SensitivePermission(
            "android.permission.POST_NOTIFICATIONS", "发送通知",
            "能给你推送通知", RiskLevel.LOW, "notification"
        )
    )

    /**
     * 扫描权限审计
     */
    suspend fun scanPermissions(context: Context): AuditReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val allApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        // 按权限分组
        val grouped = mutableMapOf<String, MutableList<AppPermissionInfo>>()

        for (pkgInfo in allApps) {
            val requested = pkgInfo.requestedPermissions ?: continue
            if (requested.isEmpty()) continue

            val app = pkgInfo.applicationInfo ?: continue
            val appName = pm.getApplicationLabel(app).toString()
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            for (perm in SENSITIVE_PERMISSIONS) {
                if (perm.permission in requested) {
                    val isGranted = pm.checkPermission(perm.permission, app.packageName) ==
                            PackageManager.PERMISSION_GRANTED
                    grouped.getOrPut(perm.permission) { mutableListOf() }.add(
                        AppPermissionInfo(
                            packageName = app.packageName,
                            appName = appName,
                            appInfo = app,
                            isSystem = isSystem,
                            isGranted = isGranted
                        )
                    )
                }
            }
        }

        // 组装分组报告（只显示有 App 申请的权限）
        val groups = SENSITIVE_PERMISSIONS.mapNotNull { perm ->
            val apps = grouped[perm.permission] ?: return@mapNotNull null
            if (apps.isEmpty()) return@mapNotNull null
            PermissionGroup(
                permission = perm,
                apps = apps.sortedWith(
                    compareByDescending<AppPermissionInfo> { it.isGranted }
                        .thenByDescending { it.isSystem }
                        .thenBy { it.appName.lowercase() }
                ),
                grantedCount = apps.count { it.isGranted }
            )
        }

        // 统计高风险 App 数量（去重）
        val highRiskApps = mutableSetOf<String>()
        for (group in groups) {
            if (group.permission.riskLevel == RiskLevel.HIGH) {
                group.apps.forEach { highRiskApps.add(it.packageName) }
            }
        }

        AuditReport(
            groups = groups,
            totalApps = highRiskApps.size,
            highRiskAppCount = highRiskApps.size
        )
    }
}
