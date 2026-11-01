package com.huijui.sunext.edition.core.root

import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * HUI 引擎的实际工作形态。
 *
 * 这是这次重构的核心：**状态必须由「引擎」决定，不能由「扫到哪个管理器」决定。**
 *
 * 旧逻辑拿 `RootManagerDetector` 的结果直接当标题：
 * ```
 * detected == HUIJUI -> "工作中 (HUI)"
 * else              -> "工作中 (${shortName} 兼容)"
 * ```
 * 于是出现两种自相矛盾的显示：
 *   · HUI 的 socket 明明在回 uid=0，但系统里同时存在 kernelsu.ko 的痕迹，
 *     detector 返回 KERNELSU → 标题写成「工作中 (KSU 兼容)」，把自己否定了；
 *   · 只有 HUI 的安装目录残留、引擎根本没起来，detector 仍可能返回 HUIJUI
 *     → 标题写成「工作中 (HUI)」，骗了用户。
 *
 * 现在分成四个互斥形态：
 */
enum class HuiEngine {
    /** 纯血：HUI socket 有响应，且没有别的管理器插一脚。 */
    HUI_NATIVE,

    /** 叠加：HUI 在跑，但同时还检测到别的管理器（KSU/Magisk…）。 */
    HUI_ALONGSIDE,

    /** 残留：有 HUI 的安装痕迹（包/目录），但引擎没响应 —— 通常是刷了一半或模块没加载。 */
    HUI_STALE,

    /** 借用：HUI 完全没起来，Root 是别的管理器给的。 */
    BORROWED
}

sealed interface HuiRootStatus {
    /** 还没探测过。 */
    data object Unknown : HuiRootStatus

    /** 确认没有 Root。 */
    data object NotRooted : HuiRootStatus

    /** 作弊哨兵触发的环境锁定。 */
    data class Locked(val until: Long) : HuiRootStatus

    /** 工作中。 */
    data class Working(
        val engine: HuiEngine,
        val manager: RootManager,
        val detail: String
    ) : HuiRootStatus

    val isWorking: Boolean get() = this is Working
}

/**
 * Root 状态唯一真相源。
 *
 * 探测策略：
 *   1. 进程内单飞（Mutex），同一时刻只有一条探测在跑。
 *   2. 1.2s 节流，避免主页刷新按钮被连点打成风暴。
 *   3. 探测超时**不谎报**成「未 Root」，保留上一次结论（超时 ≠ 没权限）。
 *   4. 「未 Root」需要连续两次确认才落地，防止守护进程重启瞬间的假阴性闪屏。
 *   5. 管理器类型单独 60s 缓存 —— 这东西变化极慢，没必要每次都查。
 */
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

    suspend fun refresh(force: Boolean = false): HuiRootStatus = lock.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < THROTTLE_MS) {
            return@withLock _status.value
        }
        lastRefreshAt = now
        _isRefreshing.value = true
        try {
            // 锁定优先：锁定期间连 su 都不用探，直接给出明确状态
            if (EnvironmentPurge.isLocked()) {
                val locked = HuiRootStatus.Locked(AppSettings.cheatLockUntil)
                _status.value = locked
                AppSettings.hasRoot = false
                return@withLock locked
            }

            val engine = withTimeoutOrNull(PROBE_TIMEOUT_MS) { RootEngineVerifier.checkStatus() }

            // 超时：保持上一次结论。把「探测不到」当成「没 Root」是最误导人的做法。
            if (engine == null) {
                return@withLock _status.value
            }

            if (!engine.isWorking) {
                // 探测过程中可能刚好被哨兵锁上
                if (engine.mode == "LOCKED") {
                    val locked = HuiRootStatus.Locked(AppSettings.cheatLockUntil)
                    _status.value = locked
                    AppSettings.hasRoot = false
                    return@withLock locked
                }

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
            val engineKind = classifyEngine(engine, manager)

            val working = HuiRootStatus.Working(
                engine = engineKind,
                manager = manager,
                detail = describe(engineKind, manager, engine.message)
            )
            _status.value = working
            AppSettings.hasRoot = true
            working
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * 形态判定，优先级从强到弱：
     *   原生 > 叠加 > 残留 > 借用
     * 依据是**引擎自己的证据**（socket 是否回包），管理器类型只作为补充信息。
     */
    private fun classifyEngine(
        engine: RootEngineVerifier.EngineStatus,
        manager: RootManager
    ): HuiEngine {
        val huiSocketUp = engine.huiSocketUp || engine.mode == "HUI"
        val huiInstalled = manager == RootManager.HUIJUI || manager == RootManager.HUIJUI_ULTRA

        val foreign = manager != RootManager.HUIJUI &&
            manager != RootManager.HUIJUI_ULTRA &&
            manager != RootManager.UNKNOWN &&
            manager != RootManager.NONE

        return when {
            // socket 有响应，且没识别出别的管理器 → 纯血
            huiSocketUp && !foreign -> HuiEngine.HUI_NATIVE
            // socket 有响应，但同时识别出别的管理器 → 叠加共存
            huiSocketUp -> HuiEngine.HUI_ALONGSIDE
            // 没有 socket，但有 HUI 安装痕迹 → 残留
            huiInstalled -> HuiEngine.HUI_STALE
            // 都没有 → 借用别人的 Root
            else -> HuiEngine.BORROWED
        }
    }

    private fun describe(kind: HuiEngine, manager: RootManager, engineMessage: String): String =
        when (kind) {
            HuiEngine.HUI_NATIVE -> "纯血引擎运行中"
            HuiEngine.HUI_ALONGSIDE ->
                if (manager == RootManager.UNKNOWN || manager == RootManager.NONE) {
                    "HUI 运行中 · 另有未知管理器痕迹"
                } else {
                    "HUI 运行中 · 同时检测到 ${manager.displayName}"
                }
            HuiEngine.HUI_STALE -> "检测到 HUI 安装痕迹，但引擎未响应（模块可能没加载）"
            HuiEngine.BORROWED ->
                if (manager == RootManager.UNKNOWN || manager == RootManager.NONE) {
                    "已取得 Root · 无法识别管理器 · HUI 引擎未启动"
                } else {
                    "${manager.displayName} 提供 Root · HUI 引擎未启动"
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
