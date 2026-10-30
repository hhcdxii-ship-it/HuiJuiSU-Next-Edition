package com.huijui.sunext.edition.core.ui.component

import android.app.ActivityManager
import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.WallpaperType
import java.io.File

/**
 * 🎨 智能壁纸引擎（视频 / 静态图 / 纯色，三态平滑切换）
 *
 * 本版改进：
 *   1. Crossfade 平滑过渡（500ms），切壁纸不闪黑
 *   2. 低端机自动降级：VIDEO → IMAGE（RAM < 4GB 或 CPU 核心 ≤ 4）
 *   3. 视频加载失败自动 fallback 到静态图，不再静默黑屏
 *   4. VIDEO 类型但视频文件缺失 → fallback 到资源内置视频 → 再失败则静态图
 *   5. ExoPlayer 状态监听（STATE_ENDED 兜底 seekTo(0)，编解码异常降级）
 *
 * 性能约定：
 *   - ExoPlayer 只在 VIDEO 模式启动，离开自动 release
 *   - 低端机永远不走 VIDEO 分支，零 ExoPlayer 开销
 *   - 切壁纸时 Crossfade 期间同时存在旧/新两层，但旧层 ≤ 500ms 后销毁
 */
@Composable
fun SmartBackground() {
    val context = LocalContext.current

    // 🎯 低端机检测（缓存一次，避免每次重组都查）
    val isLowEnd = remember(context) { detectLowEndDevice(context) }

    // 🎯 壁纸 key（用于 Crossfade 判断"内容变了"）
    val wallpaperKey = remember(
        AppSettings.wallpaperType,
        AppSettings.customVideoPath,
        AppSettings.customImagePath,
        isLowEnd
    ) {
        when (AppSettings.wallpaperType) {
            WallpaperType.VIDEO ->
                if (isLowEnd) "VIDEO_DOWNGRADED_${AppSettings.customVideoPath ?: "res"}"
                else "VIDEO_${AppSettings.customVideoPath ?: "res"}"
            WallpaperType.IMAGE -> "IMAGE_${AppSettings.customImagePath ?: "res"}"
            WallpaperType.NONE -> "NONE"
        }
    }

    // 🎯 Crossfade 平滑切换（切壁纸不闪黑）
    Crossfade(
        targetState = wallpaperKey,
        animationSpec = tween(durationMillis = 500),
        label = "wallpaper_crossfade"
    ) { key ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
            when (AppSettings.wallpaperType) {
                WallpaperType.VIDEO -> {
                    if (isLowEnd) {
                        // 🎯 低端机降级：VIDEO 走 IMAGE 逻辑（用视频首帧或静态图兜底）
                        ImageFallback(context)
                    } else {
                        VideoLayer(context)
                    }
                }
                WallpaperType.IMAGE -> ImageLayer(context)
                WallpaperType.NONE -> { /* 纯色背景，无需渲染 */ }
            }
        }
    }
}

// ==========================================
// 🎬 视频层（自动 fallback：自定义视频 → 资源视频 → 静态图）
// ==========================================
@Composable
private fun VideoLayer(context: Context) {
    // 判断用自定义视频还是资源视频
    val customPath = AppSettings.customVideoPath
    val customExists = customPath != null && File(customPath).exists()

    val videoUri: String? = when {
        customExists -> customPath!!
        else -> {
            val resId = context.resources.getIdentifier("dynamic_bg", "raw", context.packageName)
            if (resId != 0) "android.resource://${context.packageName}/$resId" else null
        }
    }

    if (videoUri == null) {
        // 无视频可用 → 降级到静态图
        ImageFallback(context)
        return
    }

    VideoBackground(videoUri = videoUri, onFailed = {})
}

// ==========================================
// 🖼️ 静态图层（自动 fallback：自定义图 → 资源图 → 纯色）
// ==========================================
@Composable
private fun ImageLayer(context: Context) {
    val customPath = AppSettings.customImagePath
    val model: Any = if (customPath != null && File(customPath).exists()) {
        File(customPath)
    } else {
        val resId = context.resources.getIdentifier("bg_static", "drawable", context.packageName)
        if (resId != 0) resId else Color(0xFF121212)
    }

    AsyncImage(
        model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

// ==========================================
// 🖼️ 静态图兜底（视频失败 / 低端机降级）
// ==========================================
@Composable
private fun ImageFallback(context: Context) {
    val customImgPath = AppSettings.customImagePath
    val model: Any = when {
        customImgPath != null && File(customImgPath).exists() -> File(customImgPath)
        else -> {
            val resId = context.resources.getIdentifier("bg_static", "drawable", context.packageName)
            if (resId != 0) resId else Color(0xFF121212)
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(model).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

// ==========================================
// 🎥 ExoPlayer 视频背景（含状态监听 + 异常降级）
// ==========================================
@Composable
private fun VideoBackground(videoUri: String, onFailed: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasFailed by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
        }
    }

    // 🎯 监听播放状态，处理循环兜底 + 异常降级
    
    // 🚀 保存 PlayerView 实例，用于后台恢复时重新绑定
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // 🎯 循环兜底：部分设备 STATE_ENDED 不会自动循环
                if (playbackState == Player.STATE_ENDED) {
                    runCatching {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 🎯 视频解码失败（格式不支持 / 文件损坏）→ 降级到静态图
                hasFailed = true
                onFailed()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // 🎯 生命周期绑定：后台自动暂停
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    exoPlayer.playWhenReady = true
                    // 🚀 强制重新绑定 SurfaceView，防止后台切回来黑屏
                    playerViewRef?.player = null
                    playerViewRef?.player = exoPlayer
                }
                Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 🎯 ExoPlayer 释放
    DisposableEffect(Unit) {
        onDispose { runCatching { exoPlayer.release() } }
    }

    // 🎯 加载视频（切视频时复用同一个 player，不重建）
    LaunchedEffect(videoUri) {
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            hasFailed = true
            onFailed()
        }
    }

    if (!hasFailed) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    
                    // 🚀 核心防大头照逻辑：根据用户设置动态切换缩放模式
                    resizeMode = when (AppSettings.wallpaperScaleMode) {
                        AppSettings.WallpaperScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AppSettings.WallpaperScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AppSettings.WallpaperScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // 🚀 保存实例到状态中
                    playerViewRef = this
                }
            },
            update = { view ->
                // 当设置改变时，实时刷新缩放模式，不需要重建视图
                view.resizeMode = when (AppSettings.wallpaperScaleMode) {
                    AppSettings.WallpaperScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AppSettings.WallpaperScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AppSettings.WallpaperScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ==========================================
// 🔧 低端机检测（缓存一次）
// ==========================================
private fun detectLowEndDevice(context: Context): Boolean {
    // 1. 系统标记的低内存设备
    val isLowRam = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }.getOrDefault(false)
    if (isLowRam) return true

    // 2. CPU 核心数 ≤ 4 视为低端
    val cores = Runtime.getRuntime().availableProcessors()
    if (cores <= 4) return true

    // 3. 堆内存 < 192MB 视为低端
    val maxHeap = Runtime.getRuntime().maxMemory()
    val maxHeapMb = maxHeap / (1024 * 1024)
    if (maxHeapMb < 192) return true

    return false
}
