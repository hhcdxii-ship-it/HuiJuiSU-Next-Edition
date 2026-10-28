package com.huijui.su.next

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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.ui.component.SmartBackground
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

data class NavItem(val route: String, val title: String, val icon: ImageVector)
enum class NavPosition { BOTTOM, LEFT, RIGHT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.huijui.su.next.core.utils.HighRefreshRateHelper.enableMaxRefreshRate(this)
        AppSettings.init(this)
        setContent {
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

    LaunchedEffect(Unit) {
        AppSettings.hasRoot = HuijuiShell.isRooted()
    }

    var navPosition by remember { mutableStateOf(NavPosition.BOTTOM) }

    val navItems = listOf(
        NavItem("home", "主页", Icons.Default.Home),
        NavItem("tool", "工具", Icons.Default.GridView),
        NavItem("superuser", "超管", Icons.Default.Security),
        NavItem("module", "模块", Icons.Default.Extension),
        NavItem("settings", "设置", Icons.Default.Settings)
    )

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

    // 🚀 全局壁纸与遮罩，解决切换页面黑屏闪烁
    Box(modifier = Modifier.fillMaxSize()) {
        SmartBackground()
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(AppSettings.wallpaperDimAlpha * 0.1f), Color.Black.copy(AppSettings.wallpaperDimAlpha * 0.4f)))
        ))

        Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(200, easing = FastOutSlowInEasing)) },
                popEnterTransition = { fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -it / 10 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(tween(200, easing = FastOutSlowInEasing)) }
            ) {
                composable("home") { ToolboxScreen() }
                composable("tool") { ToolboxScreen() } 
                composable("superuser") { SuperUserScreen() }
                composable("module") { ModuleScreen() }
                composable("settings") { SettingsScreen(onNavigateToTheme = { navController.navigate("themesettings") }) }
                composable("themesettings") { ThemeSettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }

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
        Modifier.background(Color.Black.copy(alpha = 0.3f))
    }

    val containerModifier = Modifier
        .then(if (isHorizontal) Modifier.height(72.dp).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)
        else Modifier.width(72.dp).padding(vertical = 24.dp).systemBarsPadding())
        .then(if (isHorizontal) Modifier.fillMaxWidth() else Modifier.fillMaxHeight())
        .clip(RoundedCornerShape(36.dp))
        .then(bgModifier)
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.2f), Color.White.copy(0.05f))), RoundedCornerShape(36.dp))

    Box(modifier = containerModifier) {
        Box(
            modifier = Modifier
                .offset { IntOffset(if (isHorizontal) animatedOffset.value.roundToInt() else 0, if (isHorizontal) 0 else animatedOffset.value.roundToInt()) }
                .then(if (isHorizontal) Modifier.width(with(LocalDensity.current) { itemSize.toDp() }).fillMaxHeight()
                      else Modifier.height(with(LocalDensity.current) { itemSize.toDp() }).fillMaxWidth())
                .padding(6.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.White.copy(0.05f))))
        )

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
    val isRestricted = (item.route == "superuser" || item.route == "module") && !AppSettings.hasRoot
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed && !isRestricted) 0.75f else if (isSelected && !isRestricted) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "icon_scale"
    )
    
    val finalColor = when {
        isRestricted -> Color.White.copy(0.2f)
        isSelected -> AppSettings.accentColor.color
        else -> Color.White.copy(0.6f)
    }
    
    val badgeCount = when (item.route) {
        "superuser" -> AppSettings.grantedApps.size
        "module" -> 0
        else -> 0
    }

    Box(
        modifier = modifier.clickable(
            enabled = !isRestricted,
            interactionSource = interactionSource,
            indication = null
        ) { onClick(item.route) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = Color(0xFFE57373),
                            contentColor = Color.White
                        ) {
                            Text(badgeCount.toString(), fontSize = 10.sp)
                        }
                    }
                }
            ) {
                Icon(
                    item.icon, contentDescription = item.title,
                    tint = finalColor,
                    modifier = Modifier.size(22.dp).scale(iconScale)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                item.title, 
                color = finalColor,
                fontSize = 10.sp, 
                fontWeight = if (isSelected && !isRestricted) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
