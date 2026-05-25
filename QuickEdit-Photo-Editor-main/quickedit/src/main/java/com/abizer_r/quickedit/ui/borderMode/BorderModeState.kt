package com.abizer_r.quickedit.ui.borderMode

import android.graphics.Color as AndroidColor
import com.abizer_r.quickedit.utils.BorderGradientPreset
import com.abizer_r.quickedit.utils.BorderPreset

data class BorderModeState(
    val borderColorArgb: Int = AndroidColor.BLACK,
    val borderThickness: Float = 14f,
    val borderBlurRadius: Float = 4f,
    val borderPreset: BorderPreset = BorderPreset.SOLID,
    val borderGradientPreset: BorderGradientPreset? = null,
    val isApplyingBorder: Boolean = false
)
