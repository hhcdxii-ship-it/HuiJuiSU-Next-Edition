package com.huijui.su.next.feature.toolbox

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.root.FlashLogTranslator
import com.huijui.su.next.core.root.HuijuiFlashManager
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.component.rememberPausableInfiniteTransition
import com.huijui.su.next.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PendingStatus { PENDING, PREPARING, FLASHING, SUCCESS, FAILED }

data class PendingImage(
    val uri: Uri,
    val fileName: String,
    var detectedPartition: String? = null,
    var status: PendingStatus = PendingStatus.PENDING,
    var statusText: String = "等待中",
    var errorMsg: String = ""
)

@Composable
fun FlashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var trainingMode by remember { mutableStateOf(true) }
    var advancedMode by remember { mutableStateOf(false) }
    var pendingImages by remember { mutableStateOf<List<PendingImage>>(emptyList()) }
    var isFlashing by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var overallStage by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newItems = uris.map { uri ->
                PendingImage(
                    uri = uri,
                    fileName = queryFileName(context, uri) ?: "unknown.img"
                )
            }
            pendingImages = pendingImages + newItems
            scope.launch {
                isDetecting = true
                val updated = pendingImages.map { item ->
                    if (item.detectedPartition != null) item
                    else {
                        val result = withContext(Dispatchers.IO) { detectPartition(context, item) }
                        item.copy(
                            detectedPartition = result.first,
                            statusText = if (result.first != null) "已识别：${result.first}" else "无法识别",
                            status = if (result.first != null) PendingStatus.PENDING else PendingStatus.FAILED,
                            errorMsg = result.second
                        )
                    }
                }
                pendingImages = updated
                isDetecting = false
            }
        }
    }

    val completedCount = pendingImages.count { it.status == PendingStatus.SUCCESS }
    val totalCount = pendingImages.size

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
            Text("分区刷入", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "选择 IMG · 智能识别 · 三层防护防变砖",
                color = Color.White.copy(0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
            ) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFC107).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Security, null, tint = Color(0xFFFFC107), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("训练模式", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (trainingMode) "只识别分区，不执行刷入" else "⚠️ 关闭训练，允许真实刷入",
                                    color = if (trainingMode) Color.White.copy(0.5f) else Color(0xFFEF4444),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = trainingMode,
                                onCheckedChange = { trainingMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFFFC107),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFEF4444)
                                )
                            )
                        }
                    }
                }

                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("高级模式", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (advancedMode) "已开启：GKI 不匹配时允许强制刷入" else "关闭：内核版本不匹配直接拒绝",
                                    color = if (advancedMode) Color(0xFFEF4444) else Color.White.copy(0.5f),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = advancedMode,
                                onCheckedChange = { advancedMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFEF4444),
                                    uncheckedThumbColor = Color.White.copy(0.5f),
                                    uncheckedTrackColor = Color.White.copy(0.1f)
                                )
                            )
                        }
                    }
                }

                // 🎯 总进度卡片（刷入中显示）
                if (isFlashing && totalCount > 0) {
                    item {
                        TotalProgressCard(
                            completed = completedCount,
                            total = totalCount,
                            stage = overallStage
                        )
                    }
                }

                item {
                    AddImageCard(
                        enabled = !isFlashing,
                        onClick = {
                            imagePicker.launch(arrayOf(
                                "application/octet-stream",
                                "application/x-raw-disk-image",
                                "*/*"
                            ))
                        }
                    )
                }

                if (pendingImages.isNotEmpty()) {
                    item {
                        Text(
                            "待刷入 (${pendingImages.size})",
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                    items(items = pendingImages, key = { it.uri.toString() }) { item ->
                        PendingImageCard(
                            item = item,
                            canRemove = !isFlashing,
                            onRemove = { pendingImages = pendingImages - item }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionButton(
                            trainingMode = trainingMode,
                            isFlashing = isFlashing,
                            isDetecting = isDetecting,
                            onClick = {
                                if (trainingMode) {
                                    scope.launch {
                                        isDetecting = true
                                        val cleared = pendingImages.map {
                                            it.copy(
                                                detectedPartition = null,
                                                status = PendingStatus.PENDING,
                                                statusText = "检测中",
                                                errorMsg = ""
                                            )
                                        }
                                        pendingImages = cleared
                                        val done = cleared.map { item ->
                                            val r = withContext(Dispatchers.IO) { detectPartition(context, item) }
                                            item.copy(
                                                detectedPartition = r.first,
                                                status = if (r.first != null) PendingStatus.PENDING else PendingStatus.FAILED,
                                                statusText = if (r.first != null) "已识别：${r.first}" else "无法识别",
                                                errorMsg = r.second
                                            )
                                        }
                                        pendingImages = done
                                        isDetecting = false
                                        Toast.makeText(context, "训练完成，共 ${done.size} 个文件已识别", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    showConfirmDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showConfirmDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = {
                    Text(
                        if (advancedMode) "⚠️ 高级模式刷入" else "⚠️ 确认刷入",
                        color = Color(0xFFEF4444)
                    )
                },
                text = {
                    Text(
                        buildString {
                            if (advancedMode) {
                                appendLine("⚠️ 高级模式已开启，将允许内核版本不匹配时强制刷入。")
                                appendLine()
                            }
                            appendLine("将向以下 ${pendingImages.size} 个分区写入镜像：")
                            pendingImages.forEach {
                                appendLine("· ${it.fileName} → ${it.detectedPartition ?: "未知"}")
                            }
                            appendLine()
                            append("刷入前会自动备份原分区，失败自动回滚。")
                        },
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showConfirmDialog = false
                        isFlashing = true
                        overallStage = "开始刷入…"
                        pendingImages = pendingImages.map {
                            it.copy(status = PendingStatus.PENDING, statusText = "等待中", errorMsg = "")
                        }

                        scope.launch {
                            val tasks = pendingImages.map {
                                HuijuiFlashManager.FlashTask(uri = it.uri, fileName = it.fileName)
                            }
                            val results = HuijuiFlashManager.flashMultipleImages(
                                context = context,
                                tasks = tasks,
                                advancedMode = advancedMode,
                                onTranslated = { translated ->
                                    scope.launch(Dispatchers.Main) {
                                        when (translated) {
                                            is FlashLogTranslator.TranslatedLine.Text -> {
                                                overallStage = translated.text
                                                updatePendingStatus(pendingImages, translated.text)?.let {
                                                    pendingImages = it
                                                }
                                            }
                                            is FlashLogTranslator.TranslatedLine.Progress -> overallStage = translated.text
                                            is FlashLogTranslator.TranslatedLine.Result -> overallStage = translated.message
                                            else -> {}
                                        }
                                    }
                                }
                            )

                            withContext(Dispatchers.Main) {
                                val resultMap = results.associateBy { it.fileName }
                                pendingImages = pendingImages.map { img ->
                                    val r = resultMap[img.fileName]
                                    if (r == null) img
                                    else img.copy(
                                        status = if (r.success) PendingStatus.SUCCESS else PendingStatus.FAILED,
                                        statusText = if (r.success) "刷入成功" else "刷入失败",
                                        errorMsg = if (r.success) "" else r.log.take(120)
                                    )
                                }
                                isFlashing = false
                                overallStage = ""
                                val ok = results.count { it.success }
                                Toast.makeText(context, "完成 $ok/${results.size}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Text(
                            if (advancedMode) "高级模式确认" else "确认刷入",
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showConfirmDialog = false }) {
                        Text("取消", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }
}

// ==========================================
// 🎯 总进度卡片（刷入中显示，带圆环动画）
// ==========================================
@Composable
private fun TotalProgressCard(completed: Int, total: Int, stage: String) {
    val accent = AppSettings.accentColor.color
    val targetProgress = if (total > 0) completed.toFloat() / total else 0f
    val progress by animateFloatAsState(targetProgress, spring(dampingRatio = 0.6f, stiffness = 200f), label = "totalProgress")
    val infinite = rememberPausableInfiniteTransition(label = "progressRotate")
    val rotate by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "rotate"
    )
    val breath by infinite.animateFloat(
        0.95f, 1.05f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 圆形进度环
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                // 外圈旋转光晕
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer { rotationZ = rotate; scaleX = breath; scaleY = breath }
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    accent.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    accent.copy(alpha = 0.5f)
                                )
                            ),
                            CircleShape
                        )
                )
                // 内部暗底
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                )
                // 中心数字
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$completed",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "/ $total",
                        color = Color.White.copy(0.5f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "刷入中",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (stage.isNotBlank()) stage else "准备中…",
                    color = Color.White.copy(0.8f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 线性进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(0.6f), accent)
                                )
                            )
                    )
                }
            }
        }
    }
}

// ==========================================
// 🎴 待刷入文件卡片（活力动画）
// ==========================================
@Composable
private fun PendingImageCard(
    item: PendingImage,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val isActive = item.status == PendingStatus.FLASHING || item.status == PendingStatus.PREPARING
    val isSuccess = item.status == PendingStatus.SUCCESS
    val isFailed = item.status == PendingStatus.FAILED

    val statusColor = when (item.status) {
        PendingStatus.PENDING -> Color(0xFF64B5F6)
        PendingStatus.PREPARING -> Color(0xFFFFC107)
        PendingStatus.FLASHING -> AppSettings.accentColor.color
        PendingStatus.SUCCESS -> Color(0xFF81C784)
        PendingStatus.FAILED -> Color(0xFFEF4444)
    }

    val statusIcon = when (item.status) {
        PendingStatus.PENDING -> Icons.Default.Memory
        PendingStatus.PREPARING -> Icons.Default.Security
        PendingStatus.FLASHING -> Icons.Default.PlayArrow
        PendingStatus.SUCCESS -> Icons.Default.CheckCircle
        PendingStatus.FAILED -> Icons.Default.Error
    }

    // 🎨 只在 active 状态开无限动画
    val infinite = rememberPausableInfiniteTransition(label = "card_${item.fileName}")

    val rotateBorder by if (isActive) {
        infinite.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(2500, easing = LinearEasing)),
            label = "rotateBorder"
        )
    } else remember { mutableStateOf(0f) }

    val iconBreath by if (isActive) {
        infinite.animateFloat(
            1f, 1.15f,
            infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "iconBreath"
        )
    } else remember { mutableStateOf(1f) }

    val textAlpha by if (isActive) {
        infinite.animateFloat(
            0.5f, 1f,
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "textAlpha"
        )
    } else remember { mutableStateOf(1f) }

    // 🎉 成功弹跳（一次性）
    val successScale = remember { Animatable(1f) }
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            successScale.animateTo(1.06f, spring(dampingRatio = 0.3f, stiffness = 500f))
            successScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        } else {
            successScale.snapTo(1f)
        }
    }

    // 💥 失败抖动（一次性）
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isFailed) {
        if (isFailed) {
            listOf(10f, -10f, 6f, -6f, 3f, -3f, 0f).forEach { target ->
                shakeOffset.animateTo(target, tween(40))
            }
        } else {
            shakeOffset.snapTo(0f)
        }
    }

    // ✨ 成功光晕扩散（一次性）
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            glowAlpha.animateTo(0.5f, tween(300))
            glowAlpha.animateTo(0f, tween(700))
        } else {
            glowAlpha.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            translationX = shakeOffset.value
            scaleX = successScale.value
            scaleY = successScale.value
        }
    ) {
        // 成功光晕（扩散后消失）
        if (glowAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF81C784).copy(alpha = glowAlpha.value),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(AppSettings.cardCornerRadius.dp)
                    )
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Box {
                // 刷入中：旋转彩色扫光边框
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { rotationZ = rotateBorder }
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        statusColor.copy(alpha = 0.35f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Transparent,
                                        statusColor.copy(alpha = 0.35f)
                                    )
                                ),
                                RoundedCornerShape(AppSettings.cardCornerRadius.dp)
                            )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = if (isActive) iconBreath else 1f
                                scaleY = if (isActive) iconBreath else 1f
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive) {
                            CircularProgressIndicator(
                                color = statusColor,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.fileName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (item.errorMsg.isNotBlank()) item.errorMsg else item.statusText,
                            color = statusColor.copy(alpha = if (isActive) textAlpha else 1f),
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (canRemove) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                .clickable { onRemove() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, "移除", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun updatePendingStatus(
    list: List<PendingImage>,
    translatedText: String
): List<PendingImage>? {
    val flashingMatch = Regex("正在刷入\\s+(\\S+)").find(translatedText)
    val backupMatch = Regex("正在备份\\s+(\\S+)").find(translatedText)

    val target = flashingMatch?.groupValues?.getOrNull(1)
        ?: backupMatch?.groupValues?.getOrNull(1)
        ?: return null

    val isFlashing = flashingMatch != null
    val newStatus = if (isFlashing) PendingStatus.FLASHING else PendingStatus.PREPARING
    val newText = if (isFlashing) "正在刷入…" else "正在备份…"

    return list.map {
        if (it.detectedPartition == target) {
            it.copy(status = newStatus, statusText = newText)
        } else it
    }
}

private suspend fun detectPartition(
    context: android.content.Context,
    item: PendingImage
): Pair<String?, String> = withContext(Dispatchers.IO) {
    try {
        val cacheDir = java.io.File(context.cacheDir, "flash_detect").apply { mkdirs() }
        val localFile = java.io.File(cacheDir, item.fileName)
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            localFile.outputStream().use { output -> input.copyTo(output) }
        }

        val detectRes = HuijuiShell.exec("huijui_boot detect '${localFile.absolutePath}'")
        val imageType = detectRes.out.firstOrNull()?.trim() ?: "unknown"

        val targetPartition = when (imageType) {
            "boot_or_init_boot" -> {
                val hasInitBoot = HuijuiShell.exec(
                    "test -e /dev/block/by-name/init_boot && echo yes || echo no"
                ).out.firstOrNull()?.contains("yes") == true
                if (hasInitBoot) "init_boot" else "boot"
            }
            "vendor_boot" -> "vendor_boot"
            "dtbo" -> "dtbo"
            else -> null
        }

        localFile.delete()
        Pair(targetPartition, "type=$imageType")
    } catch (e: Exception) {
        Pair(null, "检测失败：${e.message}")
    }
}

@Composable
private fun AddImageCard(enabled: Boolean, onClick: () -> Unit) {
    val accent = AppSettings.accentColor.color
    GlassCard(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        onClick = if (enabled) onClick else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("选择 IMG 镜像", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "支持多选 · boot / init_boot / vendor_boot / dtbo",
                    color = Color.White.copy(0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    trainingMode: Boolean,
    isFlashing: Boolean,
    isDetecting: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "actionScale"
    )

    val accent = if (trainingMode) Color(0xFFFFC107) else Color(0xFFEF4444)
    val label = when {
        isFlashing -> "刷入中…"
        isDetecting -> "检测中…"
        trainingMode -> "重新训练检测"
        else -> "执行刷入"
    }
    val enabled = !isFlashing && !isDetecting

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(accent.copy(alpha = if (enabled) 0.9f else 0.4f))
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFlashing || isDetecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    if (trainingMode) Icons.Default.Security else Icons.Default.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }
}
