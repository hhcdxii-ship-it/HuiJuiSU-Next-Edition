package com.huijui.su.next.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun DynamicVideoBackground() {
    val context = LocalContext.current
    // 查找 raw 目录下的 dynamic_bg.mp4
    val videoResId = context.resources.getIdentifier("dynamic_bg", "raw", context.packageName)
    
    if (videoResId != 0) {
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$videoResId")
                setMediaItem(mediaItem)
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
            }
        }
        DisposableEffect(Unit) {
            onDispose { exoPlayer.release() }
        }
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
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)))
    }
}
