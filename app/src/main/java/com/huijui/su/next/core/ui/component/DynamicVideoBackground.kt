package com.huijui.su.next.core.ui.component

import android.view.ViewGroup
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
                val videoPath = AppSettings.customVideoPath
                if (videoPath != null && File(videoPath).exists()) {
                    VideoBackground(videoUri = videoPath)
                } else {
                    val videoResId = context.resources.getIdentifier("dynamic_bg", "raw", context.packageName)
                    if (videoResId != 0) {
                        VideoBackground(videoUri = "android.resource://${context.packageName}/$videoResId")
                    }
                }
            }
            WallpaperType.IMAGE -> {
                val imagePath = AppSettings.customImagePath
                val model = if (imagePath != null && File(imagePath).exists()) {
                    File(imagePath)
                } else {
                    val imageResId = context.resources.getIdentifier("bg_static", "drawable", context.packageName)
                    if (imageResId != 0) imageResId else Color(0xFF121212)
                }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(model).build(),
                    contentDescription = null, 
                    contentScale = ContentScale.Crop, 
                    modifier = Modifier.fillMaxSize()
                )
            }
            WallpaperType.NONE -> { }
        }
    }
}

@Composable
private fun VideoBackground(videoUri: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(videoUri) {
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
            exoPlayer.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
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
        modifier = Modifier.fillMaxSize()
    )
}
