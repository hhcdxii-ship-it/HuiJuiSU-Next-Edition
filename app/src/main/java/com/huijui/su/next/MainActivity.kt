package com.huijui.su.next
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.huijui.su.next.core.utils.AppSettings
import com.huijui.su.next.core.utils.AnimationStyle
import com.huijui.su.next.core.utils.MaterialStyle
import com.huijui.su.next.feature.module.ModuleScreen
import com.huijui.su.next.feature.settings.SettingsScreen
import com.huijui.su.next.feature.settings.ThemeSettingsScreen
import com.huijui.su.next.feature.superuser.SuperUserScreen
import com.huijui.su.next.feature.toolbox.ToolboxScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlin.math.roundToInt

data class NavItem(val route: String, val title: String, val icon: ImageVector)

// 🚀 定义导航栏位置枚举（为了方便测试，先写死，后续可以接入 AppSettings 设置）
enum class NavPosition { BOTTOM, LEFT, RIGHT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.huijui.su.next.core.utils.HighRefreshRateHelper.enableMaxRefreshRate(this)
        AppSettings.init(this)
        setContent {
            // 🚀 这里原本有个 background(Color.Black)，我给它去掉了！
            // 这样切页面的瞬间，底层不会漏出死黑，而是露出上一个页面的背景
            Box(modifier = Modifier.fillMaxSize()) {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val hazeState = remember { HazeState() }

    // 🚀 这里控制导航栏位置：BOTTOM (悬浮底栏), LEFT (左侧栏), RIGHT (右侧栏)
    var navPosition by remember { mutableStateOf(NavPosition.BOTTOM) }

    val navItems = listOf(
        NavItem("home", "主页", Icons.Default.Home),
        NavItem("superuser", "超管", Icons.Default.Security),
        NavItem("module", "模块", Icons.Default.ShoppingCart),
        NavItem("settings", "设置", Icons.Default.Settings)
    )

    // 根据位置计算给 NavHost 的留白，防止内容被遮挡
    val contentPadding = when (navPosition) {
        NavPosition.BOTTOM -> PaddingValues(bottom = 110.dp)
        NavPosition.LEFT -> PaddingValues(start = 100.dp)
        NavPosition.RIGHT -> PaddingValues(end = 100.dp)
    }

    val navAlignment = when (navPosition) {
        NavPosition.BOTTOM -> Alignment.BottomCenter
        NavPosition.LEFT -> Alignment.CenterStart
        NavPosition.RIGHT -> Alignment.CenterEnd
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层内容区
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding).haze(state = hazeState)) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                // 🚀 丝滑转场：彻底干掉原版引发黑屏的 scaleOut + 长 fadOut
                // 现在的动画：旧页面 200ms 迅速淡出，新页面微滑 + 300ms 淡入
                enterTransition = {
                    fadeIn(animationSpec = tween(300)) +
                    slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(300, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300)) +
                    slideInHorizontally(initialOffsetX = { -it / 6 }, animationSpec = tween(300, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                }
            ) {
                composable("home") { ToolboxScreen() }
                composable("superuser") { SuperUserScreen() }
                composable("module") { ModuleScreen() }
                composable("settings") { SettingsScreen(onNavigateToTheme = { navController.navigate("themesettings") }) }
                composable("themesettings") { ThemeSettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }

        // 液态导航栏
        if (AppSettings.materialStyle != MaterialStyle.NONE) {
            Box(modifier = Modifier.align(navAlignment)) {
                DraggableGlassNavBar(
                    navItems = navItems,
                    currentRoute = currentRoute,
                    position = navPosition,
                    hazeState = hazeState,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DraggableGlassNavBar(
    navItems: List<NavItem>,
    currentRoute: String?,
    position: NavPosition,
    hazeState: HazeState,
    onTabSelected: (String) -> Unit
) {
    val selectedIndex = navItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    var totalSize by remember { mutableStateOf(0f) }
    var itemSize by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val animatedOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }

    val isHorizontal = position == NavPosition.BOTTOM

    val animSpec = when (AppSettings.animationStyle) {
        AnimationStyle.SPRING -> spring<Float>(dampingRatio = 0.75f, stiffness = 300f)
        AnimationStyle.SMOOTH -> tween((300 * AppSettings.animationSpeedScale).toInt(), easing = FastOutSlowInEasing)
        AnimationStyle.NONE -> tween(0)
    }

    LaunchedEffect(selectedIndex, totalSize) {
        if (!isDragging && totalSize > 0) {
            animatedOffset.animateTo(selectedIndex * itemSize, animSpec)
        }
    }

    val bgModifier = if (AppSettings.materialStyle == MaterialStyle.GLASS) {
        Modifier.hazeChild(
            state = hazeState,
            style = HazeStyle(backgroundColor = Color.Transparent, tint = HazeTint(Color.White.copy(alpha = 0.08f)), blurRadius = 20.dp)
        )
    } else {
        Modifier.background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
    }

    // 容器 Modifier
    val containerModifier = Modifier
        .then(if (isHorizontal) Modifier.height(72.dp).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)
        else Modifier.width(72.dp).padding(vertical = 24.dp).systemBarsPadding())
        .then(if (isHorizontal) Modifier.fillMaxWidth() else Modifier.fillMaxHeight())
        .clip(RoundedCornerShape(36.dp))
        .then(bgModifier)
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.4f), Color.White.copy(0.05f))), RoundedCornerShape(36.dp))

    Box(modifier = containerModifier) {
        // 1. 液态滑动背景块
        Box(
            modifier = Modifier
                .offset { IntOffset(if (isHorizontal) animatedOffset.value.roundToInt() else 0, if (isHorizontal) 0 else animatedOffset.value.roundToInt()) }
                .then(if (isHorizontal) Modifier.width(with(LocalDensity.current) { itemSize.toDp() }).fillMaxHeight()
                      else Modifier.height(with(LocalDensity.current) { itemSize.toDp() }).fillMaxWidth())
                .padding(6.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.White.copy(0.05f))))
        )

        // 2. 交互与布局层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (isHorizontal) {
                        totalSize = coordinates.size.width.toFloat()
                        itemSize = totalSize / navItems.size
                    } else {
                        totalSize = coordinates.size.height.toFloat()
                        itemSize = totalSize / navItems.size
                    }
                }
                .pointerInput(isHorizontal, itemSize) {
                    if (isHorizontal) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true; dragOffset = animatedOffset.value },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (dragOffset + dragAmount).coerceIn(0f, totalSize - itemSize)
                                dragOffset = newOffset
                                scope.launch { animatedOffset.snapTo(newOffset) }
                            },
                            onDragEnd = {
                                isDragging = false
                                val nearestIndex = (animatedOffset.value / itemSize).roundToInt().coerceIn(0, navItems.size - 1)
                                onTabSelected(navItems[nearestIndex].route)
                                scope.launch { animatedOffset.animateTo(nearestIndex * itemSize, animSpec) }
                            },
                            onDragCancel = { isDragging = false; scope.launch { animatedOffset.animateTo(selectedIndex * itemSize, animSpec) } }
                        )
                    } else {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true; dragOffset = animatedOffset.value },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (dragOffset + dragAmount).coerceIn(0f, totalSize - itemSize)
                                dragOffset = newOffset
                                scope.launch { animatedOffset.snapTo(newOffset) }
                            },
                            onDragEnd = {
                                isDragging = false
                                val nearestIndex = (animatedOffset.value / itemSize).roundToInt().coerceIn(0, navItems.size - 1)
                                onTabSelected(navItems[nearestIndex].route)
                                scope.launch { animatedOffset.animateTo(nearestIndex * itemSize, animSpec) }
                            },
                            onDragCancel = { isDragging = false; scope.launch { animatedOffset.animateTo(selectedIndex * itemSize, animSpec) } }
                        )
                    }
                }
        ) {
            if (isHorizontal) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    navItems.forEach { item ->
                        NavItemLayout(item, currentRoute, Modifier.weight(1f).fillMaxHeight(), onTabSelected)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    navItems.forEach { item ->
                        NavItemLayout(item, currentRoute, Modifier.weight(1f).fillMaxWidth(), onTabSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItemLayout(item: NavItem, currentRoute: String?, modifier: Modifier, onClick: (String) -> Unit) {
    val isSelected = currentRoute == item.route
    // 🚀 注入按压手势
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 🚀 弹簧动画：按下缩成 0.75f，松开弹回 1.0f（选中时 1.15f）
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "icon_scale"
    )
    
    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null // 干掉自带的灰色水波纹
        ) { onClick(item.route) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                item.icon, contentDescription = item.title,
                tint = if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.85f),
                modifier = Modifier.size(22.dp).scale(iconScale)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                item.title, 
                color = if (isSelected) Color.White else Color.White.copy(0.75f),
                fontSize = 10.sp, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
