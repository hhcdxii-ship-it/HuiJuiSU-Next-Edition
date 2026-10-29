package com.huijui.su.next.feature.toolbox

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huijui.su.next.core.root.RootAuditProvider
import com.huijui.su.next.core.root.RootManagerDetector
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import com.huijui.su.next.core.utils.RootAuditService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RootAuditScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<RootAuditProvider.AuditReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isServiceEnabled by remember { mutableStateOf(AppSettings.rootAuditEnabled) }
    var appInfoCache by remember { mutableStateOf<Map<String, ApplicationInfo>>(emptyMap()) }

    suspend fun load() {
        isLoading = true
        RootManagerDetector.detect()
        val r = withContext(Dispatchers.IO) { RootAuditProvider.fetchRecentAudits(80) }

        val pm = context.packageManager
        val cache = mutableMapOf<String, ApplicationInfo>()
        for (entry in r.entries) {
            if (entry.packageName in cache) continue
            runCatching {
                cache[entry.packageName] = pm.getApplicationInfo(entry.packageName, 0)
            }
        }

        report = r
        appInfoCache = cache
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))

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
                    Text("Root 审计", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("实时追踪谁在请求 Root 权限", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.1f))
                        .clickable { scope.launch { load() } },
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
                        Text("拉取审计日志…", color = Color.White.copy(0.6f), fontSize = 13.sp)
                    }
                }
            } else {
                val r = report!!
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)
                ) {
                    item { AuditStatusCard(report = r) }

                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF64B5F6).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("审计通知", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (isServiceEnabled) "已开启：通知栏显示最近一次 Root 请求"
                                        else "已关闭：需要时手动刷新",
                                        color = if (isServiceEnabled) Color(0xFF64B5F6) else Color.White.copy(0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = isServiceEnabled,
                                    onCheckedChange = { want ->
                                        isServiceEnabled = want
                                        AppSettings.rootAuditEnabled = want
                                        AppSettings.save()
                                        if (want) {
                                            RootAuditService.start(context)
                                            Toast.makeText(context, "审计通知已开启", Toast.LENGTH_SHORT).show()
                                        } else {
                                            RootAuditService.stop(context)
                                            Toast.makeText(context, "审计通知已关闭", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF64B5F6),
                                        uncheckedThumbColor = Color.White.copy(0.5f),
                                        uncheckedTrackColor = Color.White.copy(0.1f)
                                    )
                                )
                            }
                        }
                    }

                    if (r.entries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Security, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("暂无 Root 请求记录", color = Color.White.copy(0.4f), fontSize = 14.sp)
                                    Text(
                                        if (!r.logcatAvailable) "logcat 不可用，请确认已授权"
                                        else "最近没有 App 请求 Root",
                                        color = Color.White.copy(0.3f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "请求记录（${r.entries.size}）",
                                color = Color.White.copy(0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                        items(items = r.entries, key = { "${it.timestamp}_${it.packageName}" }) { entry ->
                            AuditEntryCard(
                                entry = entry,
                                appInfo = appInfoCache[entry.packageName]
                            )
                        }
                    }

                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "审计日志来自 logcat 通用通道，兼容 HUI / KSU / Magisk / APatch。部分管理器日志格式差异可能导致解析遗漏。",
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
private fun AuditStatusCard(report: RootAuditProvider.AuditReport) {
    val accent = AppSettings.accentColor.color
    val logcatColor = if (report.logcatAvailable) Color(0xFF81C784) else Color(0xFFEF4444)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("当前管理器", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(report.managerName, color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("本次请求", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${report.totalRequests}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(logcatColor, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "数据源：${report.dataSource}",
                    color = logcatColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AuditEntryCard(
    entry: RootAuditProvider.AuditEntry,
    appInfo: ApplicationInfo?
) {
    val context = LocalContext.current
    val accent = AppSettings.accentColor.color
    val statusColor = if (entry.granted) Color(0xFF81C784) else Color(0xFFEF4444)
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val timeStr = remember(entry.timestamp) {
        if (entry.timestamp > 0) timeFormat.format(Date(entry.timestamp)) else "—"
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (appInfo != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(appInfo).crossfade(true).build(),
                        contentDescription = entry.appName,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Icon(Icons.Default.Security, null, tint = statusColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.appName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.packageName,
                    color = Color.White.copy(0.4f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.source,
                        color = accent.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("·", color = Color.White.copy(0.3f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        timeStr,
                        color = Color.White.copy(0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (entry.granted) "已授权" else "已拒绝",
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
