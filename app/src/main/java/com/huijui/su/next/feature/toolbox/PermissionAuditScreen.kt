package com.huijui.su.next.feature.toolbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huijui.su.next.core.root.PermissionAuditProvider
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.layout.HuijuiLayout
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PermissionAuditScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<PermissionAuditProvider.AuditReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reload() {
        isLoading = true
        report = withContext(Dispatchers.IO) { PermissionAuditProvider.scanPermissions(context) }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal)) {
            Spacer(modifier = Modifier.height(HuijuiLayout.DetailTopSpacer))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                Text("返回", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("权限审计", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("谁在用什么权限，一目了然", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.1f))
                        .clickable { scope.launch { reload() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, "刷新", tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading || report == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppSettings.accentColor.color)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("扫描应用权限…", color = Color.White.copy(0.6f), fontSize = 13.sp)
                    }
                }
            } else {
                val r = report!!
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(HuijuiLayout.CardSpacingNormal),
                    contentPadding = HuijuiLayout.ScreenContentPadding
                ) {
                    // 统计卡片
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusMedium))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Security, null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${r.highRiskAppCount} 个应用申请高风险权限",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "覆盖 ${r.groups.size} 类敏感权限",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // 权限分组列表
                    items(items = r.groups, key = { it.permission.permission }) { group ->
                        val isExpanded = group.permission.permission in expandedGroups
                        PermissionGroupCard(
                            group = group,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandedGroups = if (isExpanded) {
                                    expandedGroups - group.permission.permission
                                } else {
                                    expandedGroups + group.permission.permission
                                }
                            }
                        )
                    }

                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Security, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "点击应用可跳转到系统设置中管理权限。此页面只展示，不修改任何权限。",
                                    color = Color.White.copy(0.35f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupCard(
    group: PermissionAuditProvider.PermissionGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val (icon, color) = iconOf(group.permission.iconName)
    val riskColor = when (group.permission.riskLevel) {
        PermissionAuditProvider.RiskLevel.HIGH -> Color(0xFFEF4444)
        PermissionAuditProvider.RiskLevel.MEDIUM -> Color(0xFFFFC107)
        PermissionAuditProvider.RiskLevel.LOW -> Color(0xFF64B5F6)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.99f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "perm_${group.permission.permission}"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onToggle() }
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusMedium))
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.permission.displayName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(riskColor.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                when (group.permission.riskLevel) {
                                    PermissionAuditProvider.RiskLevel.HIGH -> "高风险"
                                    PermissionAuditProvider.RiskLevel.MEDIUM -> "中风险"
                                    PermissionAuditProvider.RiskLevel.LOW -> "低风险"
                                },
                                color = riskColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        "${group.permission.description} · ${group.apps.size} 个应用（${group.grantedCount} 已授予）",
                        color = Color.White.copy(0.5f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.White.copy(0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(0.05f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    group.apps.forEach { app ->
                        AppPermissionRow(app = app)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPermissionRow(app: PermissionAuditProvider.AppPermissionInfo) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                runCatching {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(HuijuiLayout.IconRadiusSmall))
                .background(Color.White.copy(0.05f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(app.appInfo).crossfade(true).build(),
                contentDescription = app.appName,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.appName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystem) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF64B5F6).copy(alpha = 0.18f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("系统", color = Color(0xFF64B5F6), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                app.packageName,
                color = Color.White.copy(0.35f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (app.isGranted) "已授予" else "未授予",
            color = if (app.isGranted) Color(0xFF81C784) else Color.White.copy(0.35f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun iconOf(iconName: String): Pair<ImageVector, Color> {
    return when (iconName) {
        "camera" -> Icons.Default.CameraAlt to Color(0xFFEF4444)
        "mic" -> Icons.Default.Mic to Color(0xFFA855F7)
        "location" -> Icons.Default.LocationOn to Color(0xFF64B5F6)
        "sms" -> Icons.Default.Sms to Color(0xFF0EA5B7)
        "phone" -> Icons.Default.Phone to Color(0xFF81C784)
        "contacts" -> Icons.Default.Contacts to Color(0xFFFFC107)
        "call_log" -> Icons.Default.Phone to Color(0xFFFF9E3D)
        "storage" -> Icons.Default.SdStorage to Color(0xFF8B5CF6)
        "calendar" -> Icons.Default.Phone to Color(0xFFEC4899)
        "notification" -> Icons.Default.Notifications to Color(0xFF64B5F6)
        else -> Icons.Default.Security to Color.White.copy(0.7f)
    }
}
