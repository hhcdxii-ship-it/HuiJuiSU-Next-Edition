package com.huijui.sunext.edition.core.ui.component

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.WallpaperScaleMode
import com.huijui.sunext.edition.core.utils.WallpaperType
import java.io.File

/**
 * 全屏动态背景。
 *
 * 重构前这个文件只有 84 行，但里面写死了：
 *   `val videoUri = MediaItem.fromUri("asset:///wallpapers/miku_starry.mp4")`
 * 注释还留着「🚀 强制无视旧路径，直接加载初音未来！」——也就是说，设置页里
 * `wallpaperType`(视频/图片/无)、`customVideoPath`、`customImagePath`、`wallpaperScaleMode`
 * 四组开关**一个都不生效**。用户在那里选了半天，界面纹丝不动。
 *
 * 现在三态全部接通：
 *   VIDEO → ExoPlayer 播自定义视频，没有则用内置初音视频
 *   IMAGE → Coil 加载自定义图片，没有则回退到内置视频
 *   NONE  → 什么都不画（纯黑 + 主题色微光，避免全屏死黑）
 * 缩放模式 FIT / FILL / ZOOM 同时映射到视频的 resizeMode 和图片的 contentScale。
 */
@Composable
fun SmartBackground() {
    val mode = AppSettings.wallpaperType
    val imagePath = AppSettings.customImagePath
    val hasImage = imagePath != null && File(imagePath).isFile

    when {
        mode == WallpaperType.NONE -> StaticFallback()
        mode == WallpaperType.IMAGE && hasImage -> ImageWallpaper(imagePath!!)
        else -> VideoWallpaper()
    }
}

// ─────────────────────────────────────────────────────────────────────
// 视频壁纸
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun VideoWallpaper() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val customVideo = AppSettings.customVideoPath?.let(::File)?.takeIf { it.isFile }
    val mediaUri: Uri = if (customVideo != null) {
        Uri.fromFile(customVideo)
    } else {
        Uri.parse("asset:///wallpapers/miku_starry.mp4")
    }

    var failed by remember(mediaUri) { mutableStateOf(false) }

    val exoPlayer = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f // 背景视频必须静音
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("SmartBackground", "背景视频播放失败: ${error.message}", error)
                    failed = true
                }
            })
            prepare()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    if (failed) {
        StaticFallback()
        return
    }

    val resizeMode = when (AppSettings.wallpaperScaleMode) {
        WallpaperScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        WallpaperScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        WallpaperScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            view.resizeMode = resizeMode
            if (view.player !== exoPlayer) view.player = exoPlayer
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ─────────────────────────────────────────────────────────────────────
// 图片壁纸
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ImageWallpaper(path: String) {
    val contentScale = when (AppSettings.wallpaperScaleMode) {
        WallpaperScaleMode.FIT -> ContentScale.Fit
        WallpaperScaleMode.FILL -> ContentScale.FillBounds
        WallpaperScaleMode.ZOOM -> ContentScale.Crop
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 无壁纸 / 播放失败兜底：纯黑 + 主题色微光，别给用户一块死黑
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun StaticFallback() {
    val accent = AppSettings.accentColor.color
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.055f),
                        Color.Black,
                        Color.Black
                    )
                )
            )
    )
}
