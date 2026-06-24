@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.panel.*

import androidx.compose.foundation.layout.Column
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.core.design.components.PrecisionSliderColors
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeColorArgb
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType

@Composable
internal fun LabelStrokeColorSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val displayStrokeArgb = layer.strokeColorArgb ?: 0xFF212121.toInt()

    if (showColorPicker) {
        ColorPickerDialog(
            show = true,
            type = ColorPickerType.Circle(showAlphaBar = false),
            onDismissRequest = { showColorPicker = false },
            onPickedColor = { picked ->
                onLayoutEvent(EditorEvent.UpdateStrokeColor(picked.toArgb()))
                showColorPicker = false
            },
        )
    }

    LabelColorRow(
        currentArgb = displayStrokeArgb,
        palette = strokePalette,
        onSelectColor = { onLayoutEvent(EditorEvent.UpdateStrokeColor(it)) },
        onCustomColorClick = { showColorPicker = true },
        tokens = tokens,
    )
}

@Composable
internal fun LabelStrokeWidthSection(
    layer: EditorLayer,
    sliderColors: PrecisionSliderColors,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    PrecisionSlider(
        label = stringResource(R.string.studio_label_stroke_width),
        value = layer.strokeWidthPx,
        valueRange = 0f..20f,
        onValueChange = { onLayoutEvent(EditorEvent.UpdateStrokeWidth(it)) },
        valueFormatter = { "${it.toInt()}px" },
        colors = sliderColors,
    )
}

@Composable
internal fun LabelStrokeSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    val strokeWidthSliderColors = remember(tokens) {
        tokens.toSliderColors().copy(
            thumbColor = Color(0xFF212121),
            thumbGlowColor = Color(0xFF424242),
        )
    }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        LabelStrokeColorSection(layer = layer, tokens = tokens, onLayoutEvent = onLayoutEvent)
        LabelStrokeWidthSection(
            layer = layer,
            sliderColors = strokeWidthSliderColors,
            onLayoutEvent = onLayoutEvent,
        )
    }
}

internal val strokePalette = listOf(
    Color(0xFF212121), Color(0xFFFFFFFF), Color(0xFFE53935),
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41),
)
