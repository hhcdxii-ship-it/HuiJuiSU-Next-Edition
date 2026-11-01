package com.huijui.sunext.edition.core.root

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一条「可被授权的应用」记录。
 *
 * 注意 granted **不在这里存** —— 授权集合的唯一真相是 `AppSettings.grantedApps`
 * （它本身是 Compose state，会触发重组），这里只在读取时实时投影出来，
 * 避免出现「两套 allowlist 各存一份、toggle 之后主页数字不动」这种经典事故。
 */
data class RootAppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
) {
    fun toRootApp(granted: Boolean) = RootApp(packageName, label, isSystem, granted)
}

data class RootApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val granted: Boolean
)

/**
 * 超级用户（Root 授权）数据源。
 *
 * 重构前这里是个空壳：
 *   - `SuperUserScreen` 里 `mockApps = listOf("com.termux", "com.android.shell", "com.example.hacker")`
 *     —— 三个写死的字符串，跟设备上装了什么毫无关系；
 *   - `SuperUserManager.init(context)` 全项目**从未被调用**，`appContext` 恒为 null，
 *     于是 `getAllowlist()` 永远返回空集、`saveAllowlist()` 是个空操作。
 *     表现：拨开关当场有效，退出再进全部归零，而且主页的「授权 N」永远是 0。
 *
 * 现在：应用列表走 PackageManager 真实枚举，授权集合复用 `AppSettings.grantedApps`，
 * 图标按需解码 + LruCache，不在主线程碰磁盘。
 */
object SuperUserRepository {

    private const val ICON_CACHE_ENTRIES = 160

    private val iconCache = LruCache<String, ImageBitmap>(ICON_CACHE_ENTRIES)

    /** 枚举本机所有可授权应用（不含自己）。耗时操作，必须挂在 IO 上。 */
    suspend fun loadInstalledApps(context: Context): List<RootAppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val self = context.packageName

        val entries = ArrayList<RootAppEntry>(256)
        val installed = runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }
            .getOrDefault(emptyList())

        for (info in installed) {
            val pkg = info.packageName ?: continue
            if (pkg == self) continue
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(pkg)
            val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            entries.add(RootAppEntry(pkg, label, isSystem))
        }

        // ⚠️ 排序**不能**带「已授权优先」：那样用户一拨开关，这一行就会跳到列表顶部，
        //    手指还在屏幕上，视觉上非常突兀。授权与否用「已授权」筛选tab去查。
        entries.sortWith(
            compareBy<RootAppEntry> { it.isSystem }
                .thenBy { it.label.lowercase() }
        )
        entries
    }

    /**
     * 授权 / 撤销。
     *
     * ⚠️ 以前这里只是 `AppSettings.updateGrantedApp(...)`，也就是**只记了个名单**，
     * 从来没通知底层 —— 开关拨了、名单里有了、主页计数 +1，但那个应用实际拿不到 Root。
     * 现在走 HuijuiDeviceBridge 真正下发到 HUI / KernelSU / APatch / Magisk，
     * 只有底层返回成功才更新本地名单；失败时开关会回弹，不会假装成功。
     */
    suspend fun setGranted(context: Context, packageName: String, granted: Boolean): Boolean {
        val ok = runCatching {
            if (granted) HuijuiDeviceBridge.grantRootToApp(context, packageName)
            else HuijuiDeviceBridge.revokeRootFromApp(context, packageName)
        }.getOrDefault(false)

        if (ok) AppSettings.updateGrantedApp(packageName, granted)
        return ok
    }

    fun grantedCount(): Int = AppSettings.grantedApps.size

    /**
     * 应用图标。
     *
     * 之前没有图标（纯文字列表），而「读真实应用图标」这句写在引导页文案里 ——
     * 文案和实现对不上。这里按 packageName + 像素尺寸做 key 缓存，
     * 列表滚动时不会反复 decode；换主题/换分辨率自然失效重建。
     */
    suspend fun loadIcon(context: Context, packageName: String, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val px = sizePx.coerceIn(16, 256)
            val key = "$packageName@$px"
            iconCache.get(key)?.let { return@withContext it }

            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap().also { iconCache.put(key, it) }
            }.getOrNull()
        }

    fun clearIconCache() {
        iconCache.evictAll()
    }
}
