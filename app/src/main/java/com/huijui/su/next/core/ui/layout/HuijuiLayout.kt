package com.huijui.su.next.core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * 🎨 HUI 全局布局常量
 *
 * 设计原则：
 *   - 所有页面用同一套间距/圆角规范，视觉统一
 *   - 页面级 padding 统一（顶 20 / 底 40）
 *   - 卡片间距分三档：紧凑 12dp / 常规 16dp / 大块 20dp
 *   - 图标容器圆角分三档：小 10dp / 中 12dp / 大 16dp
 *
 * 使用方式：
 *   LazyColumn(
 *       contentPadding = HuijuiLayout.ScreenContentPadding,
 *       verticalArrangement = Arrangement.spacedBy(HuijuiLayout.CardSpacingNormal)
 *   )
 */
object HuijuiLayout {

    // ==========================================
    // 页面级 padding
    // ==========================================

    val ScreenHorizontal = 20.dp
    val ScreenTop = 20.dp
    val ScreenBottom = 40.dp
    val ScreenContentPadding = PaddingValues(top = ScreenTop, bottom = ScreenBottom)

    /** 详情页顶部 padding（带返回按钮的页面，60dp 避开状态栏） */
    val DetailTopSpacer = 60.dp

    // ==========================================
    // 卡片间距
    // ==========================================

    /** 紧凑卡片间距（列表项之间，如应用列表 / 模块列表） */
    val CardSpacingDense = 12.dp

    /** 常规卡片间距（大部分页面的卡片之间） */
    val CardSpacingNormal = 16.dp

    /** 大块分隔（不同功能区之间） */
    val SectionSpacing = 20.dp

    // ==========================================
    // 组内间距
    // ==========================================

    /** 组内元素间距（标题和副标题之间） */
    val ItemSpacingSmall = 8.dp

    /** 组内元素间距（图标和文字之间） */
    val ItemSpacingMedium = 12.dp

    // ==========================================
    // 图标容器圆角（3 档统一）
    // ==========================================

    /**
     * 小图标容器（32-40dp 图标，如列表项左侧小图标）
     * 例：36dp 图标 + 10dp 圆角 = 4 倍关系，视觉协调
     */
    val IconRadiusSmall = 10.dp

    /**
     * 中图标容器（40-48dp 图标，如设置项 / 工具入口）
     * 例：44dp 图标 + 12dp 圆角 = 3.7 倍关系，最常用
     */
    val IconRadiusMedium = 12.dp

    /**
     * 大图标容器（48-56dp 图标，如头部卡片主图标）
     * 例：52dp 图标 + 16dp 圆角 = 3.25 倍关系
     */
    val IconRadiusLarge = 16.dp

    // ==========================================
    // 卡片内 padding 规范
    // ==========================================

    /**
     * ⚠️ 重要：GlassCard 内部已自带 20dp padding
     *
     * 内部如需额外 padding：
     *   - 整个卡片内容再包一层时，用 `padding(vertical = 8.dp)` 调整
     *   - 单个元素（图标/文字）不要加 padding，用 `Spacer` 隔开
     *
     * 错误示例（双重 padding，多出 8dp）：
     *   GlassCard { Column(Modifier.padding(8.dp)) { ... } }
     *
     * 正确示例：
     *   GlassCard { Column { ... } }
     *   GlassCard { Column(Modifier.padding(vertical = 8.dp)) { ... } }  // 仅调垂直
     */
    val CardContentVerticalPadding = 8.dp

    // ==========================================
    // 特大间距
    // ==========================================

    /** 特大分隔（如引导页图标和文字之间） */
    val SpacerLarge = 32.dp
}
