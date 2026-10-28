package com.huijui.su.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.canopas.composeanimatednavigationbar.AnimatedNavigationBar
import com.canopas.composeanimatednavigationbar.animatedNavigationBarItem
import com.canopas.composeanimatednavigationbar.animation.LineIndicatorAnimation
import com.huijui.su.next.feature.toolbox.ToolboxScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 整体设为透明，让壁纸透出来
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    var selectedItem by remember { mutableStateOf(0) }
    
    // 定义底栏导航项
    val navItems = listOf(
        NavItem("home", "主页", Icons.Default.Home),
        NavItem("superuser", "超级用户", Icons.Default.Security),
        NavItem("module", "模块", Icons.Default.Build),
        NavItem("settings", "设置", Icons.Default.Settings)
    )

    Scaffold(
        // 悬浮底栏设置
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                // 使用第三方动画底栏，带悬浮和圆角效果
                AnimatedNavigationBar(
                    selectedIndex = selectedItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    barColor = Color(0xFF1E1E1E).copy(alpha = 0.8f), // 半透明
                    cornerRadius = 32.dp,
                    animation = LineIndicatorAnimation()
                ) {
                    navItems.forEachIndexed { index, item ->
                        animatedNavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title, tint = Color.White) },
                            label = item.title,
                            onClick = {
                                selectedItem = index
                                navController.navigate(item.route) {
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
    ) { innerPadding ->
        // 导航路由 Host
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { ToolboxScreen() }
            composable("superuser") { 
                // 这里之后换成你的超级用户页面
                PlaceholderScreen("超级用户页面开发中") 
            }
            composable("module") { 
                PlaceholderScreen("模块管理开发中") 
            }
            composable("settings") { 
                PlaceholderScreen("设置页面开发中") 
            }
        }
    }
}

data class NavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text = text, color = Color.White)
    }
}
