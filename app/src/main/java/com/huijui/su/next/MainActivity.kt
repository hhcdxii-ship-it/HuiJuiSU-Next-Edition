package com.huijui.su.next
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.AnimationSpec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.huijui.su.next.feature.toolbox.ToolboxScreen
import com.huijui.su.next.feature.superuser.SuperUserScreen
import com.huijui.su.next.feature.module.ModuleScreen
import com.huijui.su.next.feature.settings.SettingsScreen
import com.huijui.su.next.feature.settings.ThemeSettingsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlin.math.roundToInt

data class NavItem(val route: String, val title: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 🚀 强制开启 120Hz/144Hz 高刷
        com.huijui.su.next.core.utils.HighRefreshRateHelper.enableMaxRefreshRate(this)
        AppSettings.init(this) // 初始化设置
        setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

    val navItems = listOf(
        NavItem("home", "主页", Icons.Default.Home),
        NavItem("superuser", "超级用户", Icons.Default.Security),
        NavItem("module", "模块", Icons.Default.ShoppingCart),
        NavItem("settings", "设置", Icons.Default.Settings)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                // 🚀 使用极其丝滑的 Easing 曲线和更长的动画时间
                enterTransition = {
                    androidx.compose.animation.fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                    androidx.compose.animation.scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    androidx.compose.animation.fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                    androidx.compose.animation.scaleOut(targetScale = 1.08f, animationSpec = tween(400, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    androidx.compose.animation.fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                    androidx.compose.animation.scaleIn(initialScale = 1.08f, animationSpec = tween(400, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    androidx.compose.animation.fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                    androidx.compose.animation.scaleOut(targetScale = 0.92f, animationSpec = tween(400, easing = FastOutSlowInEasing))
                }
            ) {
                composable("home") { ToolboxScreen() }
                composable("superuser") { SuperUserScreen() }
                composable("module") { ModuleScreen() }
                composable("settings") { SettingsScreen(onNavigateToTheme = { navController.navigate("themesettings") }) }
                composable("themesettings") { ThemeSettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }

        // 🚀 根据用户选择的材质风格动态渲染底栏
        if (AppSettings.materialStyle != MaterialStyle.NONE) {
            DraggableGlassBottomBar(
                navItems = navItems,
                currentRoute = currentRoute,
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

@Composable
fun DraggableGlassBottomBar(
    navItems: List<NavItem>,
    currentRoute: String?,
    hazeState: HazeState,
    onTabSelected: (String) -> Unit
) {
    val selectedIndex = navItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    var barWidth by remember { mutableStateOf(0f) }
    var itemWidth by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val animatedOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }

    // 动画参数：使用极低阻尼的弹簧参数，让滑动有“德芙”般的惯性感
    val animSpec: androidx.compose.animation.core.AnimationSpec<Float> = when (AppSettings.animationStyle) {
        AnimationStyle.SPRING -> spring(dampingRatio = 0.75f, stiffness = 250f * (1f / AppSettings.animationSpeedScale)) // 降低刚度，提升阻尼，极其丝滑
        AnimationStyle.SMOOTH -> tween((400 * AppSettings.animationSpeedScale).toInt(), easing = FastOutSlowInEasing)
        AnimationStyle.NONE -> tween(0)
    }

    LaunchedEffect(selectedIndex, barWidth) {
        if (!isDragging && barWidth > 0) {
            val target = selectedIndex * itemWidth
            animatedOffset.animateTo(target, animSpec)
        }
    }

    val bgModifier = if (AppSettings.materialStyle == MaterialStyle.GLASS) {
        Modifier.hazeChild(
            state = hazeState,
            style = HazeStyle(backgroundColor = Color.Transparent, tint = HazeTint(Color.White.copy(alpha = 0.08f)), blurRadius = 25.dp, noiseFactor = 0.05f)
        )
    } else {
        Modifier.background(Color(0xFF1E1E1E).copy(alpha = 0.9f)) // 纯色半透明
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .then(bgModifier)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.4f), Color.White.copy(0.05f))), RoundedCornerShape(36.dp))
            .onGloballyPositioned { coordinates ->
                barWidth = coordinates.size.width.toFloat()
                itemWidth = barWidth / navItems.size
                if (animatedOffset.value == 0f && selectedIndex > 0) {
                    scope.launch { scope.launch { animatedOffset.snapTo(selectedIndex * itemWidth) } }
                }
            }
            .pointerInput(barWidth, itemWidth) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true; dragOffset = animatedOffset.value },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = (dragOffset + dragAmount).coerceIn(0f, barWidth - itemWidth)
                        dragOffset = newOffset
                        if (animatedOffset.value != newOffset) {
                            scope.launch { animatedOffset.snapTo(newOffset) }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        val nearestIndex = (animatedOffset.value / itemWidth).roundToInt().coerceIn(0, navItems.size - 1)
                        onTabSelected(navItems[nearestIndex].route)
                        scope.launch { animatedOffset.animateTo(nearestIndex * itemWidth, animSpec) }
                    },
                    onDragCancel = {
                        isDragging = false
                        scope.launch { animatedOffset.animateTo(selectedIndex * itemWidth, animSpec) }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
                    .width(with(density) { itemWidth.toDp() })
                    .fillMaxHeight()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.White.copy(0.05f))))
                    .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(30.dp))
            )
        }

        navItems.forEachIndexed { index, item ->
            val isSelected = currentRoute == item.route
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.2f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f), label = "icon_scale"
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTabSelected(item.route) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(item.icon, contentDescription = item.title, tint = if (isSelected) AppSettings.accentColor.color else Color.White.copy(0.6f), modifier = Modifier.size(22.dp).scale(iconScale))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.title, color = if (isSelected) Color.White else Color.White.copy(0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
