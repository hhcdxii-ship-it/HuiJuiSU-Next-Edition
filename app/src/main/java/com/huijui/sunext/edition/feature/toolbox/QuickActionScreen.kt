package com.huijui.sunext.edition.feature.toolbox

import com.huijui.sunext.edition.core.ui.language.ZakoText

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
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.QuickActionProvider
import com.huijui.sunext.edition.core.root.HuijuiShell
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.ui.layout.HuijuiLayout
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QuickActionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busyId by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmAction by remember { mutableStateOf<QuickActionProvider.QuickAction?>(null) }

    suspend fun runAction(action: QuickActionProvider.QuickAction) {
        busyId = action.id
        
        // 🚀 强行拦截：如果没 Root，直接弹窗嘲讽！
        if (!HuijuiShell.isRooted()) {
            busyId = null
            Toast.makeText(context, "连 Root 都没有，无法执行指令", Toast.LENGTH_SHORT).show()
            return
        }
        
        val result = withContext(Dispatchers.IO) { QuickActionProvider.execute(action) }
        busyId = null
        result.getOrNull()?.let {
            lastResult = action.title to it
            Toast.makeText(context, com.huijui.sunext.edition.core.ui.language.zakoString("$it"), Toast.LENGTH_SHORT).show()
        } ?: run {
            val err = result.exceptionOrNull()?.message ?: "执行失败"
            lastResult = action.title to "失败：$err"
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HuijuiLayout.ScreenHorizontal)
        ) {
            Spacer(modifier = Modifier.height(HuijuiLayout.DetailTopSpacer))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                ZakoText("返回", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            ZakoText("快捷指令", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            ZakoText(
                "6 个安全预设 · 无需输入命令",
                color = Color.White.copy(0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(HuijuiLayout.CardSpacingNormal),
                contentPadding = HuijuiLayout.ScreenContentPadding
            ) {
                // 最近一次执行结果
                lastResult?.let { (title, result) ->
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ZakoText("最近一次：$title", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                ZakoText(
                                    result,
                                    color = Color.White.copy(0.55f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                items(items = QuickActionProvider.PRESET_ACTIONS, key = { it.id }) { action ->
                    QuickActionCard(
                        action = action,
                        isBusy = busyId == action.id,
                        onClick = {
                            if (action.needsConfirm) {
                                confirmAction = action
                            } else {
                                scope.launch { runAction(action) }
                            }
                        }
                    )
                }

                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            ZakoText(
                                "所有指令跨管理器通用，不依赖 HUI.ko。高风险指令需二次确认，避免误操作。",
                                color = Color.White.copy(0.35f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        confirmAction?.let { action ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmAction = null },
                title = { ZakoText("⚠️ ${action.title}", color = Color(0xFFFFC107)) },
                text = {
                    ZakoText(
                        action.confirmMessage,
                        color = Color.White.copy(0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmAction = null
                        scope.launch { runAction(action) }
                    }) {
                        ZakoText("确认执行", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmAction = null }) {
                        ZakoText("取消", color = Color.White.copy(0.6f))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickActionProvider.QuickAction,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    val accent = AppSettings.accentColor.color
    val (icon, iconColor) = iconOf(action.icon)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.98f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "qa_${action.id}"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        onClick = if (!isBusy) onClick else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(HuijuiLayout.IconRadiusMedium))
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        color = iconColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZakoText(
                        action.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (action.needsConfirm) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFC107).copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            ZakoText("需确认", color = Color(0xFFFFC107), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                ZakoText(action.description, color = Color.White.copy(0.5f), fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color.White.copy(0.25f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun iconOf(actionIcon: QuickActionProvider.ActionIcon): Pair<ImageVector, Color> {
    return when (actionIcon) {
        QuickActionProvider.ActionIcon.CLEAN -> Icons.Default.CleaningServices to Color(0xFF81C784)
        QuickActionProvider.ActionIcon.SCREENSHOT -> Icons.Default.Screenshot to Color(0xFF64B5F6)
        QuickActionProvider.ActionIcon.AIRPLANE -> Icons.Default.AirplanemodeActive to Color(0xFFA855F7)
        QuickActionProvider.ActionIcon.CACHE -> Icons.Default.DeleteSweep to Color(0xFFFFC107)
        QuickActionProvider.ActionIcon.NETWORK -> Icons.Default.NetworkCheck to Color(0xFF0EA5B7)
        QuickActionProvider.ActionIcon.DEVMODE -> Icons.Default.DeveloperMode to Color(0xFFEF4444)
    }
}
