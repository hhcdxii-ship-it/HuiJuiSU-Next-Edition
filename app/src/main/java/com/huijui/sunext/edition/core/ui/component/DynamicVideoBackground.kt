package com.huijui.sunext.edition.core.ui.component

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log

import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.WallpaperScaleMode
import java.io.File

@Composable
fun SmartBackground() {
    val context = LocalContext.current
    
    // 🚀 强制无视旧路径，直接加载初音未来！
    // 如果用户想要恢复自定义壁纸，以后把这里改回去即可。
    val videoUri = MediaItem.fromUri("asset:///wallpapers/miku_starry.mp4")

    // 初始化 ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(videoUri)
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f // 背景视频必须静音
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("SmartBackground", "ExoPlayer 播放失败: ${error.message}", error)
                }
            })
            prepare()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // 绑定生命周期：切后台暂停，切前台恢复
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

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            // 让初音填满整个屏幕，不留黑边
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        },
        modifier = Modifier.fillMaxSize()
    )
}
