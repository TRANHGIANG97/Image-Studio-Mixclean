@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.core.design.components.PrecisionSliderColors
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeColorArgb
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeWidthPx
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
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
    var activeSubTab by rememberSaveable { mutableStateOf(0) } // 0: Màu viền, 1: Kiểu viền, 2: Độ dày
    
    val displayStrokeColor = layer.strokeColorArgb ?: 0xFF212121.toInt()
    val hasStroke = layer.strokeColorArgb != null && layer.strokeWidthPx > 0f
    
    val sliderColors = remember(tokens) {
        tokens.toSliderColors().copy(
            thumbColor = Color(0xFF9E9E9E), // Gray thumb
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            show = true,
            type = ColorPickerType.Circle(showAlphaBar = false),
            onDismissRequest = { showColorPicker = false },
            onPickedColor = { picked ->
                onLayoutEvent(EditorEvent.UpdateStrokeColor(picked.toArgb()))
                if (layer.strokeWidthPx == 0f) {
                    onLayoutEvent(EditorEvent.UpdateStrokeWidth(2f)) // Default thickness when picking a color
                }
                showColorPicker = false
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Top navigation bar: 3 sub-tabs (Màu viền, Kiểu viền, Độ dày) + "Không viền" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3 Sub-tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabResIds = listOf(
                    R.string.studio_stroke_tab_color,
                    R.string.studio_stroke_tab_style,
                    R.string.studio_stroke_tab_width,
                )
                tabResIds.forEachIndexed { index, labelRes ->
                    val isSelected = activeSubTab == index
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) tokens.accent else tokens.textSecondary,
                        modifier = Modifier
                            .clickable { activeSubTab = index }
                            .padding(vertical = 4.dp)
                    )
                }
            }
            
            // "Không viền" button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (!hasStroke) tokens.accentSoft else Color(0xFFF5F5F5))
                    .clickable {
                        onLayoutEvent(EditorEvent.UpdateStrokeColor(0x00000000))
                        onLayoutEvent(EditorEvent.UpdateStrokeWidth(0f))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.studio_stroke_none),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (!hasStroke) tokens.accent else Color(0xFF757575)
                )
            }
        }

        // Active Tab Content
        when (activeSubTab) {
            0 -> {
                // Màu viền Tab
                LabelColorRow(
                    currentArgb = displayStrokeColor,
                    palette = strokePalette,
                    onSelectColor = { pickedColor ->
                        onLayoutEvent(EditorEvent.UpdateStrokeColor(pickedColor))
                        if (layer.strokeWidthPx == 0f) {
                            onLayoutEvent(EditorEvent.UpdateStrokeWidth(2f)) // Default thickness when choosing color
                        }
                    },
                    onCustomColorClick = { showColorPicker = true },
                    tokens = tokens,
                )
            }
            1 -> {
                // Kiểu viền Tab
                DashStylePicker(
                    currentDashArray = layer.strokeDashArray.takeIf { it.isNotEmpty() },
                    onDashSelected = { dash ->
                        onLayoutEvent(EditorEvent.UpdateStrokeDash(dash ?: emptyList()))
                        if (dash != null && layer.strokeWidthPx <= 0f) {
                            onLayoutEvent(EditorEvent.UpdateStrokeWidth(ShapeLabelDefaults.BORDER_WIDTH_PX))
                        }
                        if (dash != null && layer.strokeColorArgb == null) {
                            onLayoutEvent(EditorEvent.UpdateStrokeColor(ShapeLabelDefaults.BORDER_COLOR_ARGB))
                        }
                    },
                    tokens = tokens,
                )
            }
            2 -> {
                // Độ dày Tab
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PrecisionSlider(
                        label = stringResource(R.string.studio_stroke_tab_width),
                        value = layer.strokeWidthPx,
                        valueRange = 0f..20f,
                        onValueChange = { onLayoutEvent(EditorEvent.UpdateStrokeWidth(it)) },
                        valueFormatter = { "${it.toInt()}px" },
                        colors = sliderColors,
                        isCompact = true,
                    )

                    OutlineWeightChips(
                        currentWeightPx = layer.resolveStrokeWidthPx(),
                        onWeightSelected = { w ->
                            onLayoutEvent(EditorEvent.UpdateStrokeWidth(w))
                        },
                        tokens = tokens,
                    )
                }
            }
        }
    }
}

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

internal val strokePalette = listOf(
    Color(0xFF212121), Color(0xFFFFFFFF), Color(0xFFE53935),
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41),
)
