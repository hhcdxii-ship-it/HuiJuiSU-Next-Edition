package com.huijui.sunext.edition.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 🚀 生命周期感知的无限动画（CPU 功耗优化核心）
 *
 * 背景：
 *   rememberInfiniteTransition 在 App 退到后台（ON_STOP）后仍满帧运行，
 *   LauncherBootSplash / EnergyCore 的光环呼吸动画在后台白白烧 CPU/GPU。
 *
 * 方案：
 *   - Animatable + while(true) 手动循环，替掉内部无限帧调度
 *   - LifecycleEventObserver 监听 ON_START / ON_STOP
 *   - ON_STOP 时 LaunchedEffect(isStarted=false) 取消当前动画循环
 *   - ON_START 时从当前值继续（animatable.value 保留，不重置）
 *
 * 接口兼容：
 *   rememberPausableInfiniteTransition(label).animateFloat(initial, target, spec, label)
 *   与 rememberInfiniteTransition 用法完全一致，只是换成 pauseable 版本
 *
 * 局限：
 *   animationSpec 仅支持 InfiniteRepeatableSpec + TweenSpec（当前项目全部是 tween）
 *   如果未来用到 keyframes / spring 无限循环，需扩展
 */
@Composable
fun rememberIsLifecycleStarted(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle
    var isStarted by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return isStarted
}

class PausableInfiniteTransition internal constructor(
    private val debugLabel: String
) {
    var isStarted: Boolean by mutableStateOf(true)
        internal set

    @Composable
    fun animateFloat(
        initialValue: Float,
        targetValue: Float,
        animationSpec: InfiniteRepeatableSpec<Float>,
        label: String = "FloatAnimation"
    ): State<Float> {
        // 从 InfiniteRepeatableSpec 里提取 TweenSpec，fallback 到 1s 线性
        val tweenSpec: TweenSpec<Float> =
            (animationSpec.animation as? TweenSpec<Float>) ?: tween(1000)
        val animatable = remember(this, label) { Animatable(initialValue) }

        LaunchedEffect(isStarted) {
            if (isStarted) {
                while (true) {
                    try {
                        animatable.animateTo(targetValue, animationSpec = tweenSpec)
                        if (animationSpec.repeatMode == RepeatMode.Reverse) {
                            animatable.animateTo(initialValue, animationSpec = tweenSpec)
                        } else {
                            animatable.snapTo(initialValue)
                        }
                    } catch (_: Exception) {
                        // 被 cancel 或组合销毁，退出循环
                        break
                    }
                }
            }
        }
        return animatable.asState()
    }
}

@Composable
fun rememberPausableInfiniteTransition(
    label: String = "PausableInfiniteTransition"
): PausableInfiniteTransition {
    val isStarted = rememberIsLifecycleStarted()
    val transition = remember(label) { PausableInfiniteTransition(label) }
    SideEffect { transition.isStarted = isStarted }
    return transition
}
