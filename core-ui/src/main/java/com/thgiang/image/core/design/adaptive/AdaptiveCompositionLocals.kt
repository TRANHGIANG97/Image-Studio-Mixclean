package com.thgiang.image.core.design.adaptive

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

data class DeviceInfo(
    val isFoldable: Boolean = false,
    val isLandscape: Boolean = false,
    val screenWidthDp: Float = 0f,
    val screenHeightDp: Float = 0f,
    val density: Float = 1f
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val LocalWindowSizeClass = staticCompositionLocalOf {
    WindowSizeClass.calculateFromSize(DpSize(Dp(360f), Dp(640f)))
}

val LocalEditorLayoutMode = staticCompositionLocalOf<EditorLayoutMode> { EditorLayoutMode.Mobile }

val LocalDeviceInfo = staticCompositionLocalOf { DeviceInfo() }
