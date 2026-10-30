package com.huijui.sunext.edition.feature.onboarding

import com.huijui.sunext.edition.core.ui.language.ZakoText

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.huijui.sunext.edition.core.ui.adaptive.rememberAdaptiveLayoutInfo
import com.huijui.sunext.edition.core.ui.component.rememberPausableInfiniteTransition
import com.huijui.sunext.edition.core.utils.AccentColor
import com.huijui.sunext.edition.core.utils.AppSettings
import com.huijui.sunext.edition.core.utils.MaterialStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class PageType {
    INTRO, CONFIG_MODE, CONFIG_ACCENT, CONFIG_MATERIAL, CONFIG_LANGUAGE, TIPS, FINAL
}

data class OnboardingPageData(
    val type: PageType,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
    val title: String,
    val subtitle: String,
    val desc: String = ""
)

private val onboardingPages = listOf(
    OnboardingPageData(PageType.INTRO, Icons.Default.Security, Color(0xFFF59E0B), "WELCOME",
        "欢迎回家，机友", "从今天起，你的设备真正属于你",
        "HuiJuiSU Next 不只是一个 Root 管理器，它是一个有温度、有生命的控制台。接下来 13 步，带你认识它、配置它、拥有它。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Memory, Color(0xFFEA8A00), "PURE KERNEL",
        "纯血内核，自己写的那种", "脱离 KSU / Magisk，一行行堆出来的 HUI.ko",
        "自研 C 内核模块 + Hsud 守护进程 + 一键热接管。没有套皮，没有借用，每一行都是自己干的。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Folder, Color(0xFF3B82F6), "HOT MOUNT",
        "模块热挂载，零重启", "刷完就能用，不用再等那一分钟",
        "本地 ZIP 直接热挂载，配合 HUI 内核接管，全程零重启体验。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.AdminPanelSettings, Color(0xFF6366F1), "SUPERUSER",
        "每个应用，你说得算", "精细到单个应用的 Root 权限",
        "读取真实应用图标，开关一拨，授权即刻生效。谁该有、谁不该有，你心里有数。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Build, Color(0xFFA855F7), "PATCHER",
        "镜像修补，一键生成", "init_boot / boot 重打包，电脑刷入",
        "A13+ 强制走 init_boot，解包、替换、注入 rc、重打包全自动。防呆拦截已在底层生效。"),
    OnboardingPageData(PageType.INTRO, Icons.Default.Fingerprint, Color(0xFFEC4899), "DETECTOR",
        "环境检测，硬核到底", "KSU / Magisk / APatch / 越狱，一个都跑不掉",
        "RootEnvironmentDetector + RootEngineVerifier 双保险，检查专属进程、属性、/dev/huijui 设备节点。"),
        OnboardingPageData(PageType.CONFIG_ACCENT, Icons.Default.Palette, Color(0xFFEF4444), "STEP 2 / 4",
        "挑一个主题色", "四种性格，你的偏好", "可在设置里随时更换"),
    OnboardingPageData(PageType.CONFIG_MATERIAL, Icons.Default.Layers, Color(0xFF3B82F6), "STEP 3 / 4",
        "材质风格", "卡片长什么样，你说了算", "毛玻璃 / 实心 / 无材质三档"),
    OnboardingPageData(PageType.CONFIG_LANGUAGE, Icons.Default.Translate, Color(0xFFA855F7), "STEP 4 / 4",
        "对话风格", "正常模式 还是 杂鱼模式？", "随时可以在设置里改"),
    OnboardingPageData(PageType.INTRO, Icons.Default.AdminPanelSettings, Color(0xFFF59E0B), "FIRST RUN",
        "首次进入，请放行 Root", "只有授权后才能解锁全部功能",
        "如果没授权，底部「超管」入口会变灰。点击能量球刷新状态时，系统弹窗直接允许即可。"),
    OnboardingPageData(PageType.TIPS, Icons.Default.TipsAndUpdates, Color(0xFF0EA5B7), "PRO TIPS",
        "几个小技巧", "老司机都这么用"),
    OnboardingPageData(PageType.FINAL, Icons.Default.Celebration, Color(0xFF16A34A), "READY",
        "准备好了吗？", "接下来，交给 HUI", "所有配置已保存，点击右下角进入你的纯血控制台。")
)

// ==========================================
// ☀️ 动态亮色背景引擎
// ==========================================
@Composable
private fun BrightDynamicBackground(accent: Color) {
    val animatedAccent by animateColorAsState(accent, tween(700), label = "bgAccent")
    val infinite = rememberPausableInfiniteTransition(label = "bgMotion")
    val t1 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(33000, easing = LinearEasing)), label = "t1")
    val t2 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(45000, easing = LinearEasing)), label = "t2")
    val t3 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(57000, easing = LinearEasing)), label = "t3")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val twoPi = 2f * PI.toFloat()

        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                Color(0xFFFFFDF9), Color(0xFFFFF3E4),
                animatedAccent.copy(alpha = 0.14f).compositeOver(Color(0xFFFFFDF9))
            ))
        ))
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(animatedAccent.copy(alpha = 0.38f), Color.Transparent),
            center = Offset(w * (0.25f + 0.18f * sin(t1 * twoPi)), h * (0.22f + 0.12f * cos(t1 * twoPi))),
            radius = w * 0.95f)))
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(Color(0xFFFFB74D).copy(alpha = 0.26f), Color.Transparent),
            center = Offset(w * (0.78f + 0.15f * cos(t2 * twoPi)), h * (0.30f + 0.10f * sin(t2 * twoPi))),
            radius = w * 0.80f)))
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(Color(0xFF80DEEA).copy(alpha = 0.22f), Color.Transparent),
            center = Offset(w * (0.50f + 0.25f * sin(t3 * twoPi)), h * (0.78f + 0.10f * cos(t3 * twoPi))),
            radius = w * 0.70f)))
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            startY = 0f, endY = h * 0.45f)))
    }
}

// ==========================================
// 🚀 引导页主入口（折叠屏铰链避让）
// ==========================================
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val adaptiveInfo = rememberAdaptiveLayoutInfo()

    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (old != null) controller.isAppearanceLightStatusBars = old }
    }

    var isAnimReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) isAnimReady = false
                else { delay(500); isAnimReady = true }
            }
    }

    DisposableEffect(Unit) {
        onDispose { if (AppSettings.isInitialized) AppSettings.save() }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage > 0 || pagerState.currentPage > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val currentPageData = onboardingPages[pagerState.currentPage]
    val currentAccent = currentPageData.accent
    val animatedAccent by animateColorAsState(currentAccent, tween(400), label = "accent")
    val isLast = pagerState.currentPage == onboardingPages.lastIndex

    Box(modifier = Modifier.fillMaxSize()) {
        BrightDynamicBackground(accent = currentAccent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // 🚀 折叠屏铰链避让
                .padding(adaptiveInfo.hingePadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
            }

            HorizontalPager(
                userScrollEnabled = false,
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                OnboardingPage(pageOffset = pageOffset, data = onboardingPages[page], adaptiveInfo = adaptiveInfo)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapsuleIndicator(
                    count = onboardingPages.size,
                    currentPage = pagerState.currentPage,
                    accent = animatedAccent
                )
                Spacer(modifier = Modifier.weight(1f))
                NextButton(
                    enabled = isAnimReady,
                    isLast = isLast,
                    accent = animatedAccent,
                    onClick = {
                        if (isAnimReady) {
                            isAnimReady = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isLast) onFinish()
                            else scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

// ==========================================
// 🎨 单页分发
// ==========================================
@Composable
private fun OnboardingPage(
    pageOffset: Float,
    data: OnboardingPageData,
    adaptiveInfo: com.huijui.sunext.edition.core.ui.adaptive.AdaptiveLayoutInfo
) {
    val absOffset = abs(pageOffset).coerceIn(0f, 1f)
    val isConfig = data.type == PageType.CONFIG_MODE || data.type == PageType.CONFIG_ACCENT
            || data.type == PageType.CONFIG_MATERIAL || data.type == PageType.CONFIG_LANGUAGE

    val pageModifier = if (isConfig) {
        Modifier.fillMaxSize()
    } else {
        val scale = (1f - absOffset * 0.25f).coerceIn(0.7f, 1f)
        val alpha = (1f - absOffset * 0.9f).coerceIn(0f, 1f)
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
    }

    Box(modifier = pageModifier, contentAlignment = Alignment.Center) {
        when (data.type) {
            PageType.INTRO -> IntroContent(pageOffset, data, adaptiveInfo)
            PageType.CONFIG_MODE -> ModeConfigContent(data, adaptiveInfo)
            PageType.CONFIG_ACCENT -> AccentConfigContent(data, adaptiveInfo)
            PageType.CONFIG_MATERIAL -> MaterialConfigContent(data, adaptiveInfo)
            PageType.CONFIG_LANGUAGE -> LanguageConfigContent(data, adaptiveInfo)
            PageType.TIPS -> TipsContent(data)
            PageType.FINAL -> FinalContent(data)
        }
    }
}

// ==========================================
// 📖 图文介绍页（折叠屏左右分栏）
// ==========================================
@Composable
private fun IntroContent(
    pageOffset: Float,
    data: OnboardingPageData,
    adaptiveInfo: com.huijui.sunext.edition.core.ui.adaptive.AdaptiveLayoutInfo
) {
    val iconParallaxX = pageOffset * 160f
    val textParallaxX = pageOffset * 320f
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.35f)
    val midAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.15f)

    val infinite = rememberPausableInfiniteTransition(label = "breath")
    val haloScale by infinite.animateFloat(0.85f, 1.15f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "haloScale")
    val haloAlpha by infinite.animateFloat(0.30f, 0.65f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "haloAlpha")
    val ringRotate by infinite.animateFloat(0f, 360f,
        infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "ringRotate")

    // 图标组合（分栏/竖屏共用）
    val iconBlock: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer { translationX = iconParallaxX }
        ) {
            Box(modifier = Modifier.size(260.dp)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale; this.alpha = haloAlpha }
                .background(Brush.radialGradient(listOf(
                    data.accent.copy(alpha = 0.55f),
                    data.accent.copy(alpha = 0.18f),
                    Color.Transparent
                )), CircleShape))
            Box(modifier = Modifier.size(190.dp)
                .graphicsLayer { rotationZ = ringRotate }
                .border(1.2.dp, Brush.sweepGradient(listOf(
                    midAccent.copy(alpha = 0.55f), Color.Transparent,
                    midAccent.copy(alpha = 0.55f), Color.Transparent,
                    midAccent.copy(alpha = 0.55f)
                )), CircleShape))
            Box(modifier = Modifier.size(150.dp)
                .shadow(24.dp, CircleShape,
                    ambientColor = data.accent.copy(alpha = 0.4f),
                    spotColor = data.accent.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = 0.95f),
                    data.accent.copy(alpha = 0.18f).compositeOver(Color.White)
                )))
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(data.icon, null, tint = deepAccent, modifier = Modifier.size(68.dp))
            }
        }
    }

    // 文字块（分栏/竖屏共用）
    val textBlock: @Composable () -> Unit = {
        Column(
            horizontalAlignment = if (adaptiveInfo.isMediumOrWider) Alignment.Start else Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { translationX = textParallaxX }
        ) {
            ZakoText("· ${data.tag} ·", color = midAccent, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            ZakoText(data.title, color = Color(0xFF1A1A1A), fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = if (adaptiveInfo.isMediumOrWider) TextAlign.Start else TextAlign.Center,
                lineHeight = 36.sp)
            Spacer(modifier = Modifier.height(10.dp))
            ZakoText(data.subtitle, color = deepAccent, fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (adaptiveInfo.isMediumOrWider) TextAlign.Start else TextAlign.Center,
                lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))
            ZakoText(data.desc, color = Color(0xFF6B6B6B), fontSize = 13.sp,
                textAlign = if (adaptiveInfo.isMediumOrWider) TextAlign.Start else TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 8.dp))
        }
    }

    if (adaptiveInfo.isMediumOrWider) {
        // 🚀 折叠屏 / 平板：左右分栏（图标左 40%，文字右 60%）
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Box(modifier = Modifier.weight(0.4f), contentAlignment = Alignment.Center) {
                iconBlock()
            }
            Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.CenterStart) {
                textBlock()
            }
        }
    } else {
        // 🚀 竖屏：现有上下堆叠
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val minHeight = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = 32.dp, vertical = 40.dp)
            ) {
                iconBlock()
                Spacer(modifier = Modifier.height(56.dp))
                textBlock()
            }
        }
    }
}

// ==========================================
// ⚙️ 配置页公共头部
// ==========================================
@Composable
private fun ConfigHeader(data: OnboardingPageData) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.35f)
    val midAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.15f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ZakoText("· ${data.tag} ·", color = midAccent, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(12.dp))
        ZakoText(data.title, color = Color(0xFF1A1A1A), fontSize = 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 34.sp)
        Spacer(modifier = Modifier.height(8.dp))
        ZakoText(data.subtitle, color = deepAccent, fontSize = 14.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (data.desc.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            ZakoText(data.desc, color = Color(0xFF7A7A7A), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

// ==========================================
// ⚙️ 配置页 - 模式选择（折叠屏双列）
// ==========================================
@Composable
private fun ModeConfigContent(
    data: OnboardingPageData,
    adaptiveInfo: com.huijui.sunext.edition.core.ui.adaptive.AdaptiveLayoutInfo
) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.25f)
    var selectedLauncher by remember { mutableStateOf(AppSettings.defaultLauncherMode) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConfigHeader(data)
        Spacer(modifier = Modifier.height(32.dp))

        if (adaptiveInfo.isMediumOrWider) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChoiceCard(
                    selected = selectedLauncher, accent = deepAccent,
                    icon = Icons.Default.GridView, title = "启动器模式",
                    subtitle = "二游风 · 能量球主页",
                    onClick = { selectedLauncher = true; AppSettings.defaultLauncherMode = true },
                    modifier = Modifier.weight(1f)
                )
                ChoiceCard(
                    selected = !selectedLauncher, accent = deepAccent,
                    icon = Icons.Default.ViewList, title = "经典模式",
                    subtitle = "老板风 · 极简列表",
                    onClick = { selectedLauncher = false; AppSettings.defaultLauncherMode = false },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            ChoiceCard(selected = selectedLauncher, accent = deepAccent,
                icon = Icons.Default.GridView, title = "启动器模式",
                subtitle = "二游风 · 能量球主页",
                onClick = { selectedLauncher = true; AppSettings.defaultLauncherMode = true },
                modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            ChoiceCard(selected = !selectedLauncher, accent = deepAccent,
                icon = Icons.Default.ViewList, title = "经典模式",
                subtitle = "老板风 · 极简列表",
                onClick = { selectedLauncher = false; AppSettings.defaultLauncherMode = false },
                modifier = Modifier.fillMaxWidth())
        }
    }
}

// ==========================================
// ⚙️ 配置页 - 主题色
// ==========================================
@Composable
private fun AccentConfigContent(
    data: OnboardingPageData,
    adaptiveInfo: com.huijui.sunext.edition.core.ui.adaptive.AdaptiveLayoutInfo
) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.25f)
    var selected by remember { mutableStateOf(AppSettings.accentColor) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConfigHeader(data)
        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = if (adaptiveInfo.isMediumOrWider) 600.dp else Int.MAX_VALUE.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                AccentColor.GREEN to "极客绿",
                AccentColor.CYBER_BLUE to "赛博蓝",
                AccentColor.FLAME_RED to "烈焰红",
                AccentColor.PURE_WHITE to "纯白"
            ).forEach { (colorEnum, name) ->
                ColorDot(color = colorEnum.color, name = name,
                    selected = selected == colorEnum, accent = deepAccent,
                    onClick = { selected = colorEnum; AppSettings.accentColor = colorEnum })
            }
        }
    }
}

// ==========================================
// ⚙️ 配置页 - 材质（折叠屏 3 列）
// ==========================================
@Composable
private fun MaterialConfigContent(
    data: OnboardingPageData,
    adaptiveInfo: com.huijui.sunext.edition.core.ui.adaptive.AdaptiveLayoutInfo
) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.25f)
    var selected by remember { mutableStateOf(AppSettings.materialStyle) }

    val items = listOf(
        Triple(MaterialStyle.GLASS, "毛玻璃", "通透，有呼吸感"),
        Triple(MaterialStyle.SOLID, "实心", "沉稳，对比度高"),
        Triple(MaterialStyle.NONE, "无材质", "极简，纯色卡片")
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConfigHeader(data)
        Spacer(modifier = Modifier.height(32.dp))

        if (adaptiveInfo.isMediumOrWider) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEach { (style, title, desc) ->
                    ChoiceCard(
                        selected = selected == style, accent = deepAccent,
                        icon = when (style) {
                            MaterialStyle.GLASS -> Icons.Default.BlurOn
                            MaterialStyle.SOLID -> Icons.Default.Square
                            MaterialStyle.NONE -> Icons.Default.CropSquare
                        },
                        title = title, subtitle = desc,
                        onClick = { selected = style; AppSettings.materialStyle = style },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            items.forEach { (style, title, desc) ->
                ChoiceCard(
                    selected = selected == style, accent = deepAccent,
                    icon = when (style) {
                        MaterialStyle.GLASS -> Icons.Default.BlurOn
                        MaterialStyle.SOLID -> Icons.Default.Square
                        MaterialStyle.NONE -> Icons.Default.CropSquare
                    },
                    title = title, subtitle = desc,
                    onClick = { selected = style; AppSettings.materialStyle = style },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ==========================================
// ⚙️ 配置页 - 语言（折叠屏双列）
// ==========================================
@Composable
private fun LanguageConfigContent(
    data: OnboardingPageData,
    adaptiveInfo: com.huijui.sunext.edition.core.ui.adaptive.AdaptiveLayoutInfo
) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.25f)
    var selected by remember { mutableStateOf(AppSettings.currentLanguage) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConfigHeader(data)
        Spacer(modifier = Modifier.height(32.dp))

        if (adaptiveInfo.isMediumOrWider) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChoiceCard(
                    selected = selected == "ZAKO", accent = deepAccent,
                    icon = Icons.Default.Favorite, title = "杂鱼模式",
                    subtitle = "嘛，反正你也是杂鱼吧？♡",
                    onClick = { selected = "ZAKO"; AppSettings.currentLanguage = "ZAKO" },
                    modifier = Modifier.weight(1f)
                )
                ChoiceCard(
                    selected = selected != "ZAKO", accent = deepAccent,
                    icon = Icons.Default.Person, title = "正常模式",
                    subtitle = "正经说话，认真干活",
                    onClick = { selected = "NORMAL"; AppSettings.currentLanguage = "NORMAL" },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            ChoiceCard(selected = selected == "ZAKO", accent = deepAccent,
                icon = Icons.Default.Favorite, title = "杂鱼模式",
                subtitle = "嘛，反正你也是杂鱼吧？♡",
                onClick = { selected = "ZAKO"; AppSettings.currentLanguage = "ZAKO" },
                modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            ChoiceCard(selected = selected != "ZAKO", accent = deepAccent,
                icon = Icons.Default.Person, title = "正常模式",
                subtitle = "正经说话，认真干活",
                onClick = { selected = "NORMAL"; AppSettings.currentLanguage = "NORMAL" },
                modifier = Modifier.fillMaxWidth())
        }
    }
}

// ==========================================
// 💡 技巧页
// ==========================================
@Composable
private fun TipsContent(data: OnboardingPageData) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.35f)
    val midAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.15f)

    val tips = listOf(
        Icons.Default.TouchApp to ("右上角点击 GridView / ViewList 图标" to "随时切换主页双形态"),
        Icons.Default.Fingerprint to ("长按能量球" to "刷新设备状态与 Root 权限"),
        Icons.Default.Dashboard to ("底部导航栏" to "主页 / 工具 / 超管 / 设置 四入口"),
        Icons.Default.Tune to ("设置里可以" to "重看引导页、换壁纸、改主题")
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ZakoText("· ${data.tag} ·", color = midAccent, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(12.dp))
        ZakoText(data.title, color = Color(0xFF1A1A1A), fontSize = 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        ZakoText(data.subtitle, color = deepAccent, fontSize = 14.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))

        tips.forEach { (icon, texts) ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(0.08f), spotColor = Color.Black.copy(0.08f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(data.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = deepAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    ZakoText(texts.first, color = Color(0xFF1A1A1A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    ZakoText(texts.second, color = Color(0xFF7A7A7A), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ==========================================
// 🎉 完成页
// ==========================================
@Composable
private fun FinalContent(data: OnboardingPageData) {
    val deepAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.35f)
    val midAccent = lerp(data.accent, Color(0xFF1A1A1A), 0.15f)

    val infinite = rememberPausableInfiniteTransition(label = "finalBreath")
    val haloScale by infinite.animateFloat(0.9f, 1.2f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "finalScale")
    val haloAlpha by infinite.animateFloat(0.35f, 0.7f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "finalAlpha")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 32.dp, vertical = 40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(280.dp)
                    .graphicsLayer { scaleX = haloScale; scaleY = haloScale; this.alpha = haloAlpha }
                    .background(Brush.radialGradient(listOf(
                        data.accent.copy(alpha = 0.55f), data.accent.copy(alpha = 0.2f), Color.Transparent
                    )), CircleShape))
                Box(modifier = Modifier.size(160.dp)
                    .shadow(30.dp, CircleShape,
                        ambientColor = data.accent.copy(alpha = 0.5f),
                        spotColor = data.accent.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(
                        Color.White, data.accent.copy(alpha = 0.25f).compositeOver(Color.White)
                    )))
                    .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(data.icon, null, tint = deepAccent, modifier = Modifier.size(80.dp))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            ZakoText("· ${data.tag} ·", color = midAccent, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            ZakoText(data.title, color = Color(0xFF1A1A1A), fontSize = 30.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            ZakoText(data.subtitle, color = deepAccent, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            ZakoText(data.desc, color = Color(0xFF6B6B6B), fontSize = 13.sp,
                textAlign = TextAlign.Center, lineHeight = 22.sp)
        }
    }
}

// ==========================================
// 🧩 选择卡片
// ==========================================
@Composable
private fun ChoiceCard(
    selected: Boolean, accent: Color, icon: ImageVector,
    title: String, subtitle: String, onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 1.02f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f), label = "choiceScale")
    val borderColor by animateColorAsState(
        if (selected) accent else Color.Black.copy(0.08f), tween(250), label = "choiceBorder")

    Row(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (selected) 16.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (selected) accent.copy(alpha = 0.4f) else Color.Black.copy(0.08f),
                spotColor = if (selected) accent.copy(alpha = 0.4f) else Color.Black.copy(0.08f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (selected) 0.22f else 0.1f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            ZakoText(title, color = Color(0xFF1A1A1A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            ZakoText(subtitle, color = Color(0xFF7A7A7A), fontSize = 12.sp)
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}

// ==========================================
// 🧩 主题色圆点
// ==========================================
@Composable
private fun ColorDot(color: Color, name: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 1.1f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 500f), label = "dotScale")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(64.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (selected) 14.dp else 4.dp, CircleShape,
                    ambientColor = color.copy(alpha = 0.5f),
                    spotColor = color.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) accent else Color.Black.copy(0.1f),
                    shape = CircleShape
                )
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null,
                    tint = if (color == Color(0xFFFFFFFF)) Color.Black else Color.White,
                    modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        ZakoText(name, color = Color(0xFF1A1A1A), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// 🧭 胶囊指示器
// ==========================================
@Composable
private fun CapsuleIndicator(count: Int, currentPage: Int, accent: Color) {
    val deepAccent = lerp(accent, Color(0xFF1A1A1A), 0.2f)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(if (isSelected) 20.dp else 5.dp,
                spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot_width_$index")
            val color by animateColorAsState(
                if (isSelected) deepAccent else Color.Black.copy(alpha = 0.15f),
                tween(300), label = "dot_color_$index")
            Box(modifier = Modifier.width(width).height(5.dp).clip(CircleShape).background(color))
        }
    }
}

// ==========================================
// 🚀 右下角下一步按钮
// ==========================================
@Composable
private fun NextButton(enabled: Boolean, isLast: Boolean, accent: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (isPressed && enabled) 0.94f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 600f), label = "nextScale")

    val deepAccent = lerp(accent, Color(0xFF1A1A1A), 0.2f)
    val lightAccent = lerp(accent, Color.White, 0.15f)

    val bgBrush = if (enabled) {
        Brush.horizontalGradient(listOf(lightAccent, deepAccent))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFE5E5E5), Color(0xFFD5D5D5)))
    }
    val textColor by animateColorAsState(
        if (enabled) Color.White else Color(0xFF9E9E9E), tween(200), label = "nextText")

    Row(
        modifier = Modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .shadow(
                elevation = if (enabled && isPressed) 6.dp else if (enabled) 14.dp else 0.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = if (enabled) accent.copy(alpha = 0.45f) else Color.Transparent,
                spotColor = if (enabled) accent.copy(alpha = 0.45f) else Color.Transparent
            )
            .clip(RoundedCornerShape(28.dp))
            .background(bgBrush)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZakoText(if (isLast) "进入控制台" else "下一步", color = textColor, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(if (isLast) Icons.Default.RocketLaunch else Icons.Default.ArrowForward,
            contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
    }
}
