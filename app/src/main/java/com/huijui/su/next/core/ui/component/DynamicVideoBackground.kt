package com.huijui.su.next.core.ui.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huijui.su.next.core.utils.AppSettings
import com.huijui.su.next.core.utils.WallpaperType
import java.io.File

@Composable
fun SmartBackground() {
    val context = LocalContext.current
    val wallpaperType = AppSettings.wallpaperType
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        when (wallpaperType) {
            WallpaperType.VIDEO -> {
                // 优先加载自定义路径的视频，如果没有，再加载 raw 默认视频
                val videoPath = AppSettings.customVideoPath
                if (videoPath != null && File(videoPath).exists()) {
                    VideoBackground(videoPath)
                } else {
                    val videoResId = context.resources.getIdentifier("dynamic_bg", "raw", context.packageName)
                    if (videoResId != 0) {
                        VideoBackground("android.resource://${context.packageName}/$videoResId")
                    }
                }
            }
            WallpaperType.IMAGE -> {
                // 优先加载自定义路径的图片，如果没有，再加载 drawable 默认图
                val imagePath = AppSettings.customImagePath
                val model = if (imagePath != null && File(imagePath).exists()) {
                    File(imagePath)
                } else {
                    val imageResId = context.resources.getIdentifier("bg_static", "drawable", context.packageName)
                    if (imageResId != 0) imageResId else Color(0xFF121212)
                }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(model).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            WallpaperType.NONE -> {
                // 纯黑背景
            }
        }
    }
}

@Composable
private fun VideoBackground(videoUri: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
        }
    }
    DisposableEffect(videoUri) { onDispose { exoPlayer.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
