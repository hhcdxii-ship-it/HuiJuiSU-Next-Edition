package com.huijui.sunext.edition.feature.toolbox

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.PartitionBackupManager
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.language.ZakoText
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPartition by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<Map<String, List<PartitionBackupManager.BackupInfo>>>(emptyMap()) }
    var isLoadingBackups by remember { mutableStateOf(true) }
    var confirmRestore by remember { mutableStateOf<PartitionBackupManager.BackupInfo?>(null) }
    var confirmDelete by remember { mutableStateOf<PartitionBackupManager.BackupInfo?>(null) }

    suspend fun reload() {
        isLoadingBackups = true
        val list = withTimeoutOrNull(8000L) { withContext(Dispatchers.IO) { PartitionBackupManager.listAllBackups() } } ?: emptyMap()
        backups = list
        isLoadingBackups = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(60.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                ZakoText("返回", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText("分区备份", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    ZakoText("所有分区均可备份 · 仅系统引导分区可恢复", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.1f)).clickable(enabled = !isBackingUp && !isRestoring) { scope.launch { reload() } }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Refresh, "刷新", tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)) {
                item { GlassCard(modifier = Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Security, null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(10.dp)); ZakoText("备份全程只读，不会修改分区 · 恢复仅限 boot / init_boot / vendor_boot / dtbo", color = Color.White.copy(0.7f), fontSize = 11.sp, lineHeight = 16.sp) } } }
                item { ZakoText("选择要备份的分区", color = Color.White.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
                items(items = PartitionBackupManager.BACKUPABLE_PARTITIONS, key = { it.first }) { (name, label) ->
                    PartitionSelectRow(name = name, label = label, selected = selectedPartition == name, hasBackup = backups.containsKey(name), onClick = { selectedPartition = if (selectedPartition == name) null else name })
                }
                if (selectedPartition != null) {
                    item { Spacer(modifier = Modifier.height(8.dp)); BackupActionButton(isBusy = isBackingUp || isRestoring, partition = selectedPartition!!, onClick = {
                        val partition = selectedPartition!!
                        scope.launch {
                            isBackingUp = true
                            try {
                                val result = withTimeoutOrNull(120000L) { withContext(Dispatchers.IO) { PartitionBackupManager.backupPartition(partition) } } ?: PartitionBackupManager.BackupResult(success = false, message = "备份超时（120s）", backupPath = "")
                                Toast.makeText(context, result.message, if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                if (result.success) { selectedPartition = null; reload() }
                            } finally { isBackingUp = false }
                        }
                    }) }
                }
                if (isLoadingBackups) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppSettings.accentColor.color, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) } }
                } else if (backups.isEmpty()) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { ZakoText("还没有备份", color = Color.White.copy(0.4f), fontSize = 14.sp) } }
                } else {
                    item { Spacer(modifier = Modifier.height(8.dp)); ZakoText("已有备份（${backups.values.sumOf { it.size }} 份）", color = Color.White.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
                    backups.toSortedMap().forEach { (partition, list) ->
                        item(key = "header_$partition") { ZakoText(partition, color = AppSettings.accentColor.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
                        items(items = list, key = { it.path }) { info ->
                            BackupItemCard(info = info, canRestore = PartitionBackupManager.canRestore(partition), onRestore = { confirmRestore = info }, onDelete = { confirmDelete = info })
                        }
                    }
                }
            }
        }

        confirmRestore?.let { info ->
            AlertDialog(onDismissRequest = { if (!isRestoring) confirmRestore = null }, title = { ZakoText("⚠️ 确认恢复", color = Color(0xFFEF4444)) }, text = { ZakoText("将把 ${info.fileName} 恢复到 /dev/block/by-name/${info.partition}。\n\n恢复过程中请勿断开连接或关机。恢复完成后建议重启设备。", color = Color.White.copy(0.85f), fontSize = 13.sp, lineHeight = 20.sp) }, confirmButton = {
                TextButton(enabled = !isRestoring, onClick = {
                    scope.launch {
                        isRestoring = true
                        try {
                            val ok = withTimeoutOrNull(120000L) { withContext(Dispatchers.IO) { PartitionBackupManager.restorePartition(info.partition, info.path) } } ?: false
                            confirmRestore = null
                            Toast.makeText(context, if (ok) "恢复成功，建议重启设备" else "恢复失败或超时", Toast.LENGTH_LONG).show()
                        } finally { isRestoring = false }
                    }
                }) { ZakoText("确认恢复", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            }, dismissButton = { TextButton(onClick = { if (!isRestoring) confirmRestore = null }) { ZakoText("取消", color = Color.White.copy(0.6f)) } }, containerColor = Color(0xFF1E1E1E))
        }

        confirmDelete?.let { info ->
            AlertDialog(onDismissRequest = { confirmDelete = null }, title = { ZakoText("删除备份", color = Color.White) }, text = { ZakoText("将删除 ${info.fileName}，删除后无法恢复，是否继续？", color = Color.White.copy(0.85f), fontSize = 13.sp) }, confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withTimeoutOrNull(8000L) { withContext(Dispatchers.IO) { PartitionBackupManager.deleteBackup(info.path) } } ?: false
                        confirmDelete = null
                        Toast.makeText(context, if (ok) "已删除" else "删除失败", Toast.LENGTH_SHORT).show()
                        if (ok) reload()
                    }
                }) { ZakoText("删除", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { ZakoText("取消", color = Color.White.copy(0.6f)) } }, containerColor = Color(0xFF1E1E1E))
        }
    }
}

@Composable
private fun PartitionSelectRow(name: String, label: String, selected: Boolean, hasBackup: Boolean, onClick: () -> Unit) {
    val accent = if (selected) AppSettings.accentColor.color else Color.White.copy(0.2f)
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(if (selected) accent else Color.Transparent).then(if (!selected) Modifier.background(Color.White.copy(0.05f)) else Modifier), contentAlignment = Alignment.Center) {
                if (selected) { Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                ZakoText(label, color = if (selected) Color.White else Color.White.copy(0.8f), fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                ZakoText("设备节点：/dev/block/by-name/$name", color = Color.White.copy(0.35f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            if (hasBackup) { ZakoText("已备份", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun BackupActionButton(isBusy: Boolean, partition: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "backupScale")
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(28.dp)).background(AppSettings.accentColor.color.copy(alpha = if (isBusy) 0.4f else 0.9f)).clickable(enabled = !isBusy, interactionSource = interactionSource, indication = null) { onClick() }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isBusy) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) } else { Icon(Icons.Default.SaveAlt, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.width(8.dp))
            ZakoText(if (isBusy) "备份中…" else "备份 $partition", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BackupItemCard(info: PartitionBackupManager.BackupInfo, canRestore: Boolean, onRestore: () -> Unit, onDelete: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
    val timeStr = remember(info.timestamp) { if (info.timestamp > 0) timeFormat.format(Date(info.timestamp)) else "未知时间" }
    val sizeMb = info.sizeBytes / 1024 / 1024
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SaveAlt, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ZakoText(info.fileName, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ZakoText("$timeStr · ${sizeMb}MB", color = Color.White.copy(0.4f), fontSize = 10.sp)
                }
                if (!canRestore) { Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); ZakoText("仅备份", color = Color(0xFFFFC107), fontSize = 10.sp) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRestore) { Box(modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF64B5F6).copy(alpha = 0.15f)).clickable { onRestore() }, contentAlignment = Alignment.Center) { ZakoText("恢复", color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
                Box(modifier = Modifier.weight(if (canRestore) 1f else 2f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEF4444).copy(alpha = 0.15f)).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); ZakoText("删除", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
