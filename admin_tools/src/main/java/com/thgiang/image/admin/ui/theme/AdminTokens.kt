package com.thgiang.image.admin.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for admin_tools module.
 * Consolidates all magic numbers from screens and components.
 */
object AdminTokens {
    // Top bar / canvas spacing for the builder screen
    val builderOuterPadding = 12.dp
    val builderDockSpacing = 10.dp
    val builderDockRadius = 28.dp
    val builderDockInnerPadding = 12.dp
    val builderSectionGap = 10.dp
    val builderTopBarRadius = 28.dp
    val builderTopBarHorizontalPadding = 10.dp
    val builderTopBarVerticalPadding = 8.dp
    val builderTopBarTitleFontSize = 16.sp
    val builderTopBarSubtitleFontSize = 11.sp
    val builderSaveButtonHeight = 36.dp
    val builderSaveButtonHorizontalPadding = 18.dp
    val builderSaveButtonFontSize = 14.sp
    val builderActionChipHeight = 48.dp
    val builderLayerChipHeight = 40.dp
    val builderCanvasTopInset = 20.dp
    val builderCanvasBottomInsetCompact = 168.dp
    val builderCanvasBottomInsetExpanded = 320.dp

    // Legacy tokens kept for compatibility with the older admin UI
    val topBarHeight = 64.dp
    val topBarIconSize = 22.dp
    val topBarSmallIconSize = 20.dp
    val topBarHorizontalPadding = 8.dp

    val bottomToolbarHorizontalPadding = 12.dp
    val bottomToolbarVerticalPadding = 6.dp

    val adminToolBarBottomPadding = 12.dp
    val adminToolBarSpacing = 12.dp
    val adminToolBarInnerSpacing = 20.dp
    val adminToolBarHorizontalPadding = 20.dp
    val adminToolBarVerticalPadding = 8.dp
    val adminToolBarCornerRadius = 99.dp
    val adminToolBarLayerSpacing = 16.dp
    val adminToolBarLayerHPadding = 16.dp

    val dashboardContentPadding = 16.dp
    val dashboardGridSpacing = 12.dp
    val dashboardCardElevation = 4.dp
    val dashboardCardInfoPadding = 12.dp
    val dashboardActionIconSize = 32.dp
    val dashboardIconInnerSize = 20.dp

    val labelFontSize = 11.sp
    val bodySmallFontSize = 11.sp
    val titleFontSize = 14.sp
    val iconFontSize = 20.sp
    val zoomLabelFontSize = 11.sp

    const val iconEnabledAlpha = 0.65f
    const val iconDisabledAlpha = 0.32f
    const val disabledButtonAlpha = 0.20f

    val saveButtonHeight = 36.dp
    val saveButtonHorizontalPadding = 18.dp
    val saveButtonEndPadding = 8.dp
    val listItemGap = 8.dp
    val layerChipPaddingHorizontal = 12.dp
    val layerChipPaddingVertical = 7.dp
}
