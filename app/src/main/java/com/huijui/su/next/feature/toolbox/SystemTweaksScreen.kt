package com.huijui.su.next.feature.toolbox

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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.SystemTweaksProvider
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemTweaksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hostsStatus by remember { mutableStateOf<SystemTweaksProvider.HostsStatus?>(null) }
    var mountStatus by remember { mutableStateOf<SystemTweaksProvider.MountStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isHostsBusy by remember { mutableStateOf(false) }
    var isMountBusy by remember { mutableStateOf(false) }
    var pendingHostsAction by remember { mutableStateOf<String?>(null) }
    var pendingRemount by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingRemountAll by remember { mutableStateOf<Boolean?>(null) }

    suspend fun reload() {
        isLoading = true
        val hs = withContext(Dispatchers.IO) { SystemTweaksProvider.getHostsStatus() }
        val ms = withContext(Dispatchers.IO) { SystemTweaksProvider.getMountStatus() }
        hostsStatus = hs
        mountStatus = ms
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

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
                    Text("系统魔改", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("广告屏蔽 · 分区可写切换", color = Color.White.copy(0.6f), fontSize = 12.sp)
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppSettings.accentColor.color)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
                ) {
                    // ================= hosts 广告屏蔽 =================
                    item {
                        Text(
                            "广告屏蔽",
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    item {
                        val hs = hostsStatus
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF81C784).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Security,
                                        null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "hosts 广告屏蔽",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (hs?.available != true) "hosts 文件不可访问"
                                        else if (hs.isEnabled) "已启用 · 屏蔽 ${hs.adBlockDomainCount} 个广告域名"
                                        else "未启用 · 当前 ${hs.currentEntries} 条记录",
                                        color = if (hs?.isEnabled == true) Color(0xFF81C784)
                                        else Color.White.copy(0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                                if (isHostsBusy) {
                                    CircularProgressIndicator(
                                        color = AppSettings.accentColor.color,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Switch(
                                        checked = hs?.isEnabled == true,
                                        enabled = hs?.available == true,
                                        onCheckedChange = { want ->
                                            pendingHostsAction = if (want) "apply" else "restore"
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF81C784),
                                            uncheckedThumbColor = Color.White.copy(0.5f),
                                            uncheckedTrackColor = Color.White.copy(0.1f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "应用后需要重启网络或设备才能完全生效。部分 App 有 DNS 缓存，需多刷新几次。",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // ================= 分区 RW/RO =================
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                "分区可写状态",
                                color = Color.White.copy(0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "一键全部 RW",
                                color = AppSettings.accentColor.color,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable(enabled = !isMountBusy) {
                                        pendingRemountAll = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(
                                "全部 RO",
                                color = Color.White.copy(0.5f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable(enabled = !isMountBusy) {
                                        pendingRemountAll = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val ms = mountStatus
                    if (ms == null || ms.partitions.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "未检测到可操作分区（可能需要先加载 HUI 内核或 root）",
                                    color = Color.White.copy(0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(items = ms.partitions, key = { it.path }) { part ->
                            PartitionRow(
                                partition = part,
                                isBusy = isMountBusy,
                                onToggle = { wantWritable ->
                                    pendingRemount = part.path to wantWritable
                                }
                            )
                        }
                    }

                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "分区 RW 为临时修改，重启后恢复只读。请勿在 RW 状态长时间挂载，误删系统文件可导致无法开机。",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= hosts 二次确认 =================
        pendingHostsAction?.let { action ->
            val isApply = action == "apply"
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingHostsAction = null },
                title = {
                    Text(
                        if (isApply) "启用广告屏蔽" else "还原原 hosts",
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        if (isApply) {
                            "将写入约 ${SystemTweaksProvider.getAdBlockDomainCount()} 个广告域名到 /system/etc/hosts。\n\n首次应用会自动备份原文件。修改可能影响部分依赖广告域名的 App，是否继续？"
                        } else {
                            "将从备份还原原 hosts 文件，广告会重新出现，是否继续？"
                        },
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingHostsAction = null
                        scope.launch {
                            isHostsBusy = true
                            val result = withContext(Dispatchers.IO) {
                                if (isApply) SystemTweaksProvider.applyHosts()
                                else SystemTweaksProvider.restoreHosts()
                            }
                            isHostsBusy = false
                            Toast.makeText(
                                context,
                                result.getOrElse { it.message ?: "操作失败" },
                                if (result.isSuccess) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            ).show()
                            reload()
                        }
                    }) {
                        Text(
                            if (isApply) "确认启用" else "确认还原",
                            color = if (isApply) Color(0xFF81C784) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingHostsAction = null }) {
                        Text("取消", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }

        // ================= 单分区 remount 二次确认 =================
        pendingRemount?.let { (path, wantWritable) ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRemount = null },
                title = {
                    Text(
                        if (wantWritable) "切换为可写" else "切换为只读",
                        color = if (wantWritable) Color(0xFFFFC107) else Color.White
                    )
                },
                text = {
                    Text(
                        if (wantWritable) {
                            "$path 将变为可写。这允许修改系统文件，但也意味着误操作风险增加。修改重启后失效，是否继续？"
                        } else {
                            "$path 将恢复只读，安全状态恢复，是否继续？"
                        },
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingRemount = null
                        scope.launch {
                            isMountBusy = true
                            val ok = withContext(Dispatchers.IO) {
                                SystemTweaksProvider.remount(path, wantWritable)
                            }
                            isMountBusy = false
                            Toast.makeText(
                                context,
                                if (ok) "$path 已${if (wantWritable) "可写" else "只读"}"
                                else "切换失败，请确认 root 权限",
                                Toast.LENGTH_SHORT
                            ).show()
                            reload()
                        }
                    }) {
                        Text("确认", color = if (wantWritable) Color(0xFFFFC107) else Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingRemount = null }) {
                        Text("取消", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }

        // ================= 全部 remount 二次确认 =================
        pendingRemountAll?.let { wantWritable ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRemountAll = null },
                title = {
                    Text(
                        if (wantWritable) "⚠️ 全部切换为可写" else "全部恢复只读",
                        color = if (wantWritable) Color(0xFFEF4444) else Color.White
                    )
                },
                text = {
                    Text(
                        if (wantWritable) {
                            "将尝试把 /system /vendor /product /system_ext 全部切换为可写。\n\n这是高风险操作，建议仅在必须修改系统文件时使用。重启后自动恢复只读，是否继续？"
                        } else {
                            "所有分区将恢复只读保护，是否继续？"
                        },
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingRemountAll = null
                        scope.launch {
                            isMountBusy = true
                            val count = withContext(Dispatchers.IO) {
                                SystemTweaksProvider.remountAll(wantWritable)
                            }
                            isMountBusy = false
                            Toast.makeText(
                                context,
                                "已切换 $count 个分区为${if (wantWritable) "可写" else "只读"}",
                                Toast.LENGTH_SHORT
                            ).show()
                            reload()
                        }
                    }) {
                        Text(
                            if (wantWritable) "确认切换" else "确认",
                            color = if (wantWritable) Color(0xFFEF4444) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingRemountAll = null }) {
                        Text("取消", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }
}

// ==========================================
// 🧩 分区行
// ==========================================
@Composable
private fun PartitionRow(
    partition: SystemTweaksProvider.PartitionMount,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val accent = AppSettings.accentColor.color
    val icon = if (partition.writable) Icons.Default.LockOpen else Icons.Default.Lock
    val color = if (partition.writable) Color(0xFFFFC107) else Color(0xFF64B5F6)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    partition.path,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${partition.fsType} · ${if (partition.writable) "可写" else "只读"}",
                    color = color,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f))
                    .clickable(enabled = !isBusy) { onToggle(!partition.writable) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (partition.writable) "切回只读" else "切换可写",
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
