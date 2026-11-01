package com.huijui.sunext.edition.core.root

import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Root 状态的唯一真相源。
 *
 * 旧链路的问题（实测会卡 3~10 秒）：
 *   主页一次刷新里，
 *     RootManagerDetector.detect()  → 内部最多 6 次独立 su exec（id / proc/modules / pm list / ps -A / test -d ×3）
 *     HuijuiShell.isRooted()        → RootEngineVerifier.checkStatus()，又是一次 su + 一次 socket 探测
 *   两条链路互相不知道对方在干嘛，超时还是各自算的。外层 withTimeoutOrNull(5000) 经常直接把
 *   整个结果丢掉，于是界面显示的是**上一次**的旧状态 —— 这就是「状态不准」的根因。
 *
 * 现在的策略：
 *   1. 进程内单飞（Mutex），同一时刻只有一条探测在跑。
 *   2. 1.2s 节流，避免主页刷新按钮被连点打成风暴。
 *   3. 探测超时**不谎报**成「未 Root」，保留上一次结论（超时 ≠ 没权限）。
 *   4. 「未 Root」需要连续两次确认才落地，防止守护进程重启瞬间的假阴性闪屏。
 *   5. 管理器类型（KernelSU / Magisk / HUI…）单独 60s 缓存 —— 这东西变化极慢，没必要每次都查。
 */
sealed interface HuiRootStatus {
    /** 还没探测过。 */
    data object Unknown : HuiRootStatus

    /** 确认没有 Root。 */
    data object NotRooted : HuiRootStatus

    /** 工作中。 */
    data class Working(
        val engine: String,
        val manager: RootManager,
        val detail: String
    ) : HuiRootStatus

    val isWorking: Boolean get() = this is Working
}

object RootStatusRepository {

    private const val THROTTLE_MS = 1_200L
    private const val PROBE_TIMEOUT_MS = 4_500L
    private const val MANAGER_TTL_MS = 60_000L

    private val lock = Mutex()

    private val _status = MutableStateFlow<HuiRootStatus>(HuiRootStatus.Unknown)
    val status: StateFlow<HuiRootStatus> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastRefreshAt = 0L
    private var failedAttempts = 0

    private var cachedManager: RootManager? = null
    private var managerCheckedAt = 0L

    /**
     * 刷新状态。并发调用会被合并，不会叠加探测。
     *
     * @param force 忽略节流（用户手动点刷新 / ON_RESUME 时用）。
     */
    suspend fun refresh(force: Boolean = false): HuiRootStatus = lock.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < THROTTLE_MS) {
            return@withLock _status.value
        }
        lastRefreshAt = now
        _isRefreshing.value = true
        try {
            val engine = withTimeoutOrNull(PROBE_TIMEOUT_MS) { RootEngineVerifier.checkStatus() }

            // 超时：保持上一次结论。把「探测不到」当成「没 Root」是旧代码最误导人的地方。
            if (engine == null) {
                return@withLock _status.value
            }

            if (!engine.isWorking) {
                failedAttempts++
                if (failedAttempts >= 2) {
                    cachedManager = null
                    managerCheckedAt = 0L
                    _status.value = HuiRootStatus.NotRooted
                    AppSettings.hasRoot = false
                }
                return@withLock _status.value
            }

            failedAttempts = 0
            val manager = resolveManager(now)
            val working = HuiRootStatus.Working(
                engine = engine.mode,
                manager = manager,
                detail = engine.message
            )
            _status.value = working
            AppSettings.hasRoot = true
            working
        } finally {
            _isRefreshing.value = false
        }
    }

    /** 手动失效缓存（装了新管理器 / 热接管之后调用）。 */
    fun invalidate() {
        cachedManager = null
        managerCheckedAt = 0L
        lastRefreshAt = 0L
        failedAttempts = 0
    }

    private suspend fun resolveManager(now: Long): RootManager {
        val cached = cachedManager
        if (cached != null && now - managerCheckedAt < MANAGER_TTL_MS) return cached
        val detected = runCatching { RootManagerDetector.detect(forceRefresh = true) }
            .getOrDefault(RootManager.UNKNOWN)
        cachedManager = detected
        managerCheckedAt = now
        return detected
    }
}
