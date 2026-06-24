@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.panel

import androidx.compose.foundation.background
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.thgiang.image.core.design.components.PrecisionSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
internal fun PanelHandle(tokens: EditorTokens = LocalEditorTokens.current) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .size(32.dp, 4.dp)
            .background(
                color = Color(0xFFD1D5DB),
                shape = RoundedCornerShape(2.dp),
            ),
    )
}

internal fun EditorTokens.toSliderColors() = PrecisionSliderColors(
    labelColor = textPrimary,
    labelActiveColor = accent,
    valuePillBackground = surfaceFloating,
    valuePillTextColor = textPrimary,
    trackColor = surfaceFloating,
    trackActiveColor = accent,
    thumbColor = Color.White,
    thumbGlowColor = accent,
    rangeLabelColor = textSecondary,
    borderColor = borderSubtle,
)

internal fun Color.toArgbInt(): Int = toArgb()
