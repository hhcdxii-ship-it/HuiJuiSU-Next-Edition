package com.huijui.sunext.edition.core.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val foldingFeatures by collectFoldingFeaturesAsState()

    return remember(windowSizeClass, foldingFeatures) {
        AdaptiveLayoutInfo(
            widthSizeClass = windowSizeClass.windowWidthSizeClass,
            heightSizeClass = windowSizeClass.windowHeightSizeClass,
            foldingFeatures = foldingFeatures
        )
    }
}

data class AdaptiveLayoutInfo(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val foldingFeatures: List<FoldingFeature>
) {
    val isCompact: Boolean get() = widthSizeClass == WindowWidthSizeClass.COMPACT
    val isMedium: Boolean get() = widthSizeClass == WindowWidthSizeClass.MEDIUM
    val isExpanded: Boolean get() = widthSizeClass == WindowWidthSizeClass.EXPANDED
    val isMediumOrWider: Boolean get() = !isCompact
    val hasOccludingHinge: Boolean get() = foldingFeatures.any { it.occlusionType == FoldingFeature.OcclusionType.FULL }
    val isSeparating: Boolean get() = foldingFeatures.any { it.isSeparating }
    val isHalfOpened: Boolean get() = foldingFeatures.any { it.state == FoldingFeature.State.HALF_OPENED }
    val hasHorizontalHinge: Boolean get() = foldingFeatures.any { it.orientation == FoldingFeature.Orientation.HORIZONTAL }
    val hasVerticalHinge: Boolean get() = foldingFeatures.any { it.orientation == FoldingFeature.Orientation.VERTICAL }

    val hingePadding: PaddingValues
        @Composable get() {
            if (!hasOccludingHinge) return PaddingValues(0.dp)
            val hinge = foldingFeatures.first { it.occlusionType == FoldingFeature.OcclusionType.FULL }
            return when (hinge.orientation) {
                FoldingFeature.Orientation.HORIZONTAL -> PaddingValues(
                    top = hinge.bounds.height().dp / 2,
                    bottom = hinge.bounds.height().dp / 2
                )
                FoldingFeature.Orientation.VERTICAL -> PaddingValues(
                    start = hinge.bounds.width().dp / 2,
                    end = hinge.bounds.width().dp / 2
                )
                else -> PaddingValues(0.dp)
            }
        }

    val isHingeCentered: Boolean get() = foldingFeatures.any { it.occlusionType == FoldingFeature.OcclusionType.FULL && it.isSeparating }
}
