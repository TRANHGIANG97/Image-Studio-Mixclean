@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.components.label

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.EditorLayer
import com.thgiang.image.studio.ui.editor.components.toSliderColors
import com.thgiang.image.studio.ui.editor.resolveStrokeColorArgb
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType

@Composable
internal fun LabelStrokeSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val sliderColors = remember(tokens) { tokens.toSliderColors() }
    val displayStrokeArgb = layer.resolveStrokeColorArgb() ?: layer.shapeColorArgb

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

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        Text(
            text = stringResource(R.string.studio_label_stroke_color),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
        LabelColorRow(
            currentArgb = displayStrokeArgb,
            palette = strokePalette,
            onSelectColor = { onLayoutEvent(EditorEvent.UpdateStrokeColor(it)) },
            onCustomColorClick = { showColorPicker = true },
            tokens = tokens,
        )
        PrecisionSlider(
            label = stringResource(R.string.studio_label_stroke_width),
            value = layer.strokeWidthPx,
            valueRange = 0f..20f,
            onValueChange = { onLayoutEvent(EditorEvent.UpdateStrokeWidth(it)) },
            valueFormatter = { "${it.toInt()}px" },
            colors = sliderColors,
        )
    }
}

private val strokePalette = listOf(
    Color(0xFF212121), Color(0xFFFFFFFF), Color(0xFFE53935),
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41),
)
