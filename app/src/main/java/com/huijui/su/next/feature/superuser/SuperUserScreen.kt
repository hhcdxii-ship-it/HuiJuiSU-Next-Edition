package com.huijui.su.next.feature.superuser

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.huijui.su.next.core.root.HuijuiDeviceBridge
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable
)

@Composable
fun SuperUserScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == "com.huijui.su.next") return@mapNotNull null // 不显示自己
                InstalledApp(
                    packageName = pkg,
                    appName = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm)
                )
            }.sortedBy { it.appName }
            
            withContext(Dispatchers.Main) {
                apps = appList
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppSettings.accentColor.color)
        }
        return
    }

    // 🚀 核心：这里是真正的授权拦截与下发
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("超级用户", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("已授权应用将直接获取 HUI 内核态 Root", color = Color.White.copy(0.6f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))
        
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps) { app ->
                val isGranted = AppSettings.grantedApps.contains(app.packageName)
                
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(app.packageName, color = Color.White.copy(0.6f), fontSize = 12.sp, maxLines = 1)
                        }
                        
                        // 🚀 拨动开关，向内核下发指令
                        LiquidSwitch(checked = isGranted) {
                            scope.launch {
                                if (!isGranted) {
                                    val success = HuijuiDeviceBridge.grantRootToApp(context, app.packageName)
                                    if (success) {
                                        AppSettings.grantedApps = AppSettings.grantedApps + app.packageName
                                        AppSettings.save()
                                    }
                                } else {
                                    HuijuiDeviceBridge.revokeRootFromApp(context, app.packageName)
                                    AppSettings.grantedApps = AppSettings.grantedApps - app.packageName
                                    AppSettings.save()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val width = 50.dp
    val height = 28.dp
    val thumbSize = 24.dp
    
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) width - thumbSize - 4.dp else 2.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "switch"
    )
    
    val trackColor = if (checked) AppSettings.accentColor.color else Color.White.copy(0.2f)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxSize().background(trackColor))
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White)
        )
    }
}
