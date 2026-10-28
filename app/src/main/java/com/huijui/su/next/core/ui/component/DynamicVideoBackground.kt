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
import com.huijui.su.next.R

/**
 * 🚀 智能壁纸引擎
 * 自动检测并加载 MP4、GIF 或 静态图片
 */
@Composable
fun SmartBackground() {
    val context = LocalContext.current
    
    // 动态获取资源 ID (防止由于文件不存在导致编译报错)
    val videoResId = getResId(context, "dynamic_bg", "raw")
    val imageResId = getResId(context, "bg_static", "drawable")
    val gifResId = getResId(context, "bg_gif", "drawable")

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // 优先级 1：MP4 动态壁纸 (硬解，60帧)
            videoResId != 0 -> {
                VideoBackground(videoResId)
            }
            // 优先级 2：GIF 动图
            gifResId != 0 -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(gifResId).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 优先级 3：静态高清图片
            imageResId != 0 -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageResId).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 优先级 4：啥都没有，纯黑背景
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)))
            }
        }
    }
}

@Composable
private fun VideoBackground(videoResId: Int) {
    val context = LocalContext.current
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
    
    // 离开页面时安全释放，防止内存泄漏和后台耗电
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
}

/**
 * 动态获取资源 ID 的工具函数
 */
fun getResId(context: Context, resName: String, type: String): Int {
    return context.resources.getIdentifier(resName, type, context.packageName)
}
