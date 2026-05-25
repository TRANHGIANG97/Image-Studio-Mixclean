package com.thgiang.image.core.design.adaptive

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

val WindowSizeClass.isCompact: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Compact

val WindowSizeClass.isMedium: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Medium

val WindowSizeClass.isExpanded: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded

sealed interface EditorLayoutMode {
    /** Phone portrait — full-screen canvas, bottom tools */
    data object Mobile : EditorLayoutMode

    /** Tablet portrait / phone landscape / foldable unfolded */
    data object Tablet : EditorLayoutMode

    /** Tablet landscape / desktop — persistent side panels */
    data object Desktop : EditorLayoutMode
}

@Composable
fun windowLayoutMode(windowSizeClass: WindowSizeClass): EditorLayoutMode {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    return when {
        windowSizeClass.isExpanded -> EditorLayoutMode.Desktop
        windowSizeClass.isMedium && isLandscape -> EditorLayoutMode.Desktop
        windowSizeClass.isMedium -> EditorLayoutMode.Tablet
        else -> EditorLayoutMode.Mobile
    }
}
