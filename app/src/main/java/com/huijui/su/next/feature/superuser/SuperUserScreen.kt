package com.huijui.su.next.feature.superuser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import com.huijui.su.next.core.utils.AppSettings

import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.SmartBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable
)

@Composable
fun SuperUserScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 模拟用户授权状态记录

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    InstalledApp(appInfo.packageName, pm.getApplicationLabel(appInfo).toString(), pm.getApplicationIcon(appInfo))
                } else null
            }.distinctBy { it.packageName }.sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                apps = appList
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().background(Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(AppSettings.wallpaperDimAlpha)))))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))
            Text("超级用户", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("应用 Root 权限管理", color = Color.White.copy(0.85f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val isGranted = AppSettings.grantedApps.contains(app.packageName)
                        
                        // 卡片弹性缩放
                        val cardScale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "card_scale"
                        )

                        GlassCard(
                            modifier = Modifier.fillMaxWidth().scale(cardScale),
                            onClick = { 
                                AppSettings.grantedApps = if (isGranted) AppSettings.grantedApps - app.packageName else AppSettings.grantedApps + app.packageName
                                AppSettings.save()
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 应用图标加柔和高斯阴影，提升立体感
                                Image(
                                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), clip = false)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(app.packageName, color = Color.White.copy(0.75f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                
                                // 🚀 纯原生手搓液态开关
                                LiquidSwitch(checked = isGranted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidSwitch(checked: Boolean) {
    // 滑块位置动画
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "thumb_offset"
    )
    
    // 背景颜色渐变
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) AppSettings.accentColor.color else Color.White.copy(0.2f),
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "bg_color"
    )

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(bgColor)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
