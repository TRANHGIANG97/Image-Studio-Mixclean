package com.abizer_r.quickedit.ui.borderMode

import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor

data class BorderModeState(
    val borderColorArgb: Int = AndroidColor.BLACK,
    val borderThickness: Float = 14f,
    val isApplyingBorder: Boolean = false
)
