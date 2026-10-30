package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.language.ZakoText

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.ArkSnapshotManager
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.component.rememberPausableInfiniteTransition
import com.huijui.sunext.edition.core.ui.layout.HuijuiLayout
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshots by remember { mutableStateOf<List<ArkSnapshotManager.SnapshotInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<ArkSnapshotManager.SnapshotInfo?>(null) }
    var pendingDelete by remember { mutableStateOf<ArkSnapshotManager.SnapshotInfo?>(null) }

    suspend fun reload() {
        isLoading = true
        snapshots = withContext(Dispatchers.IO) { ArkSnapshotManager.listSnapshots() }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal)) {
            Spacer(modifier = Modifier.height(HuijuiLayout.DetailTopSpacer))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                ZakoText("返回", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText("配置方舟", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    ZakoText("环境级快照 · 一键迁移与回滚", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.1f)).clickable { scope.launch { reload() } },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Refresh, "刷新", tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp)) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 顶部创建一个方舟按钮
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCreateDialog = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(AppSettings.accentColor.color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        ZakoText("创建新方舟", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        ZakoText("打包当前模块与配置，防砖必备", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ZakoText("历史快照 (${snapshots.size})", color = Color.White.copy(0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                }
            } else if (snapshots.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ZakoText("还没有方舟快照", color = Color.White.copy(0.4f), fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(snapshots, key = { it.path }) { snapshot ->
                        ArkTimelineItem(
                            snapshot = snapshot,
                            onRestore = { pendingRestore = snapshot },
                            onDelete = { pendingDelete = snapshot }
                        )
                    }
                }
            }
        }

        // 创建设置弹窗
        if (showCreateDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { ZakoText("创建配置方舟", color = Color.White) },
                text = { ZakoText("将要打包 /data/adb/modules 和 HUI 偏好设置。建议在刷入新模块前执行，出问题可秒级回滚。", color = Color.White.copy(0.8f), fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        scope.launch {
                            isProcessing = true
                            val res = withContext(Dispatchers.IO) { ArkSnapshotManager.createSnapshot(context, includeConfig = true) }
                            isProcessing = false
                            Toast.makeText(context, res.getOrElse { it.message ?: "失败" }, Toast.LENGTH_SHORT).show()
                            reload()
                        }
                    }) { ZakoText("打包", color = AppSettings.accentColor.color, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showCreateDialog = false }) { ZakoText("取消", color = Color.White.copy(0.6f)) } },
                containerColor = Color(0xFF1E1E1E)
            )
        }

        // 还原确认弹窗
        pendingRestore?.let { snap ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { ZakoText("⚠️ 确认还原", color = Color(0xFFFFC107)) },
                text = { ZakoText("将把系统模块与 HUI 配置回滚到 ${snap.fileName} 的状态。建议重启以完全生效。", color = Color.White.copy(0.8f), fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRestore = null
                        scope.launch {
                            isProcessing = true
                            val res = withContext(Dispatchers.IO) { ArkSnapshotManager.restoreSnapshot(snap.path, context) }
                            isProcessing = false
                            Toast.makeText(context, res.getOrElse { it.message ?: "还原失败" }, Toast.LENGTH_LONG).show()
                            reload()
                        }
                    }) { ZakoText("确认还原", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { ZakoText("取消", color = Color.White.copy(0.6f)) } },
                containerColor = Color(0xFF1E1E1E)
            )
        }

        // 删除确认弹窗
        pendingDelete?.let { snap ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { ZakoText("删除快照", color = Color.White) },
                text = { ZakoText("删除后无法恢复，确定要移除 ${snap.fileName} 吗？", color = Color.White.copy(0.8f), fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) { ArkSnapshotManager.deleteSnapshot(snap.path) }
                            reload()
                        }
                    }) { ZakoText("删除", color = Color(0xFFEF4444)) }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { ZakoText("取消", color = Color.White.copy(0.6f)) } },
                containerColor = Color(0xFF1E1E1E)
            )
        }

        // 全局处理遮罩
        if (isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                    Spacer(modifier = Modifier.height(16.dp))
                    ZakoText("方舟数据搬运中…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ArkTimelineItem(
    snapshot: ArkSnapshotManager.SnapshotInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeStr = remember(snapshot.timestamp) { if (snapshot.timestamp > 0) timeFormat.format(Date(snapshot.timestamp)) else "未知时间" }
    val accent = AppSettings.accentColor.color

    // 时间轴左侧的发光点
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(accent))
            Box(modifier = Modifier.width(2.dp).height(80.dp).background(accent.copy(alpha = 0.2f)))
        }
        Spacer(modifier = Modifier.width(8.dp))

        GlassCard(modifier = Modifier.weight(1f)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0EA5B7).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Save, null, tint = Color(0xFF0EA5B7), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ZakoText(snapshot.fileName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ZakoText("$timeStr · ${snapshot.sizeMb}", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)).clickable { onRestore() }, contentAlignment = Alignment.Center) {
                        ZakoText("还原环境", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEF4444).copy(alpha = 0.15f)).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
