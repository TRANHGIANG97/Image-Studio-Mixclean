@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.mapper.*

import androidx.compose.foundation.BorderStroke
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.core.design.components.PrecisionSliderColors
import kotlin.math.abs
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.interaction.MutableInteractionSource

private enum class FillColorMode { Solid, Gradient }
private enum class TextColorMode { Solid, Gradient }

internal enum class LabelColorTab { Background, Text }
private enum class FillSubTab { PRESETS, COLOR, OPACITY }

@Composable
internal fun LabelGradientSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    showMode: LabelColorTab? = null,
    onSubTabActiveChanged: (Boolean) -> Unit = {},
) {
    var fillMode by remember(layer.id, layer.fillGradient) {
        mutableStateOf(if (layer.fillGradient != null) FillColorMode.Gradient else FillColorMode.Solid)
    }
    var textMode by remember(layer.id, layer.textColorGradient) {
        mutableStateOf(if (layer.textColorGradient != null) TextColorMode.Gradient else TextColorMode.Solid)
    }
    var colorPickerTarget by remember { mutableStateOf<String?>(null) }
    val sliderColors = remember(tokens) { tokens.toSliderColors() }

    val fillGrad = layer.fillGradient
    val textGrad = layer.textColorGradient
    val fillColor1 = EditorGradientMapper.parseStopArgb(fillGrad, 0, layer.shapeColorArgb)
    val fillColor2 = EditorGradientMapper.parseStopArgb(fillGrad, 1, layer.shapeColorArgb)
    val fillStop1Offset = fillGrad?.colorStops?.getOrNull(0)?.offset ?: 0f
    val fillStop2Offset = fillGrad?.colorStops?.getOrNull(1)?.offset ?: 1f
    val textColor1 = EditorGradientMapper.parseStopArgb(textGrad, 0, layer.textColorArgb)
    val textColor2 = EditorGradientMapper.parseStopArgb(textGrad, 1, layer.textColorArgb)
    val fillAngle = EditorGradientMapper.linearGradientAngleDegrees(fillGrad)
    val textAngle = EditorGradientMapper.linearGradientAngleDegrees(textGrad)

    if (colorPickerTarget != null) {
        ColorPickerDialog(
            show = true,
            type = ColorPickerType.Circle(showAlphaBar = false),
            onDismissRequest = { colorPickerTarget = null },
            onPickedColor = { picked ->
                val argb = picked.toArgb()
                when (colorPickerTarget) {
                    "fill1" -> onLayoutEvent(
                        EditorEvent.UpdateFillGradient(
                            EditorGradientMapper.buildLinearGradient(argb, fillColor2, fillAngle),
                        ),
                    )
                    "fill2" -> onLayoutEvent(
                        EditorEvent.UpdateFillGradient(
                            EditorGradientMapper.buildLinearGradient(fillColor1, argb, fillAngle),
                        ),
                    )
                    "text1" -> onLayoutEvent(
                        EditorEvent.UpdateTextColorGradient(
                            EditorGradientMapper.buildLinearGradient(argb, textColor2, textAngle),
                        ),
                    )
                    "text2" -> onLayoutEvent(
                        EditorEvent.UpdateTextColorGradient(
                            EditorGradientMapper.buildLinearGradient(textColor1, argb, textAngle),
                        ),
                    )
                    "fill_custom" -> {
                        onLayoutEvent(EditorEvent.UpdateShapeColor(argb))
                        fillMode = FillColorMode.Solid
                    }
                    "text_custom" -> {
                        onLayoutEvent(EditorEvent.UpdateTextColor(argb))
                        textMode = TextColorMode.Solid
                    }
                }
                colorPickerTarget = null
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showMode == null || showMode == LabelColorTab.Background) {
            FillColorEditor(
                mode = fillMode,
                tokens = tokens,
                solidArgb = layer.shapeColorArgb,
                color1 = fillColor1,
                color2 = fillColor2,
                stop1Offset = fillStop1Offset,
                stop2Offset = fillStop2Offset,
                angle = fillAngle,
                sliderColors = sliderColors,
                onModeChange = { mode ->
                    fillMode = mode
                    if (mode == FillColorMode.Solid) {
                        onLayoutEvent(EditorEvent.UpdateFillGradient(null))
                    } else {
                        onLayoutEvent(
                            EditorEvent.UpdateFillGradient(
                                EditorGradientMapper.buildLinearGradient(fillColor1, fillColor2, fillAngle, fillStop1Offset, fillStop2Offset),
                            ),
                        )
                    }
                },
                onSolidColor = { onLayoutEvent(EditorEvent.UpdateShapeColor(it)) },
                onCustomSolid = { colorPickerTarget = "fill_custom" },
                onGradientColor1 = { colorPickerTarget = "fill1" },
                onGradientColor2 = { colorPickerTarget = "fill2" },
                onAngleChange = { angle ->
                    onLayoutEvent(
                        EditorEvent.UpdateFillGradient(
                            EditorGradientMapper.buildLinearGradient(fillColor1, fillColor2, angle, fillStop1Offset, fillStop2Offset),
                        ),
                    )
                },
                onPresetSelected = { presetAngle ->
                    onLayoutEvent(
                        EditorEvent.UpdateFillGradient(
                            EditorGradientMapper.buildLinearGradient(fillColor1, fillColor2, presetAngle, fillStop1Offset, fillStop2Offset),
                        ),
                    )
                },
                appearanceAlpha = layer.appearance.alpha,
                onLayoutEvent = onLayoutEvent,
                onSubTabActiveChanged = onSubTabActiveChanged,
            )
        }

        if (showMode == null || showMode == LabelColorTab.Text) {
            TextColorEditor(
                mode = textMode,
                tokens = tokens,
                solidArgb = layer.textColorArgb,
                color1 = textColor1,
                color2 = textColor2,
                angle = textAngle,
                sliderColors = sliderColors,
                onModeChange = { mode ->
                    textMode = mode
                    if (mode == TextColorMode.Solid) {
                        onLayoutEvent(EditorEvent.UpdateTextColorGradient(null))
                    } else {
                        onLayoutEvent(
                            EditorEvent.UpdateTextColorGradient(
                                EditorGradientMapper.buildLinearGradient(textColor1, textColor2, textAngle),
                            ),
                        )
                    }
                },
                onSolidColor = { onLayoutEvent(EditorEvent.UpdateTextColor(it)) },
                onCustomSolid = { colorPickerTarget = "text_custom" },
                onGradientColor1 = { colorPickerTarget = "text1" },
                onGradientColor2 = { colorPickerTarget = "text2" },
                onAngleChange = { angle ->
                    onLayoutEvent(
                        EditorEvent.UpdateTextColorGradient(
                            EditorGradientMapper.buildLinearGradient(textColor1, textColor2, angle),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SubTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit
) {
    val color = if (selected) tokens.accent else tokens.textPrimary
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun FillColorEditor(
    mode: FillColorMode,
    tokens: EditorTokens,
    solidArgb: Int,
    color1: Int,
    color2: Int,
    stop1Offset: Float,
    stop2Offset: Float,
    angle: Float,
    sliderColors: PrecisionSliderColors,
    onModeChange: (FillColorMode) -> Unit,
    onSolidColor: (Int) -> Unit,
    onCustomSolid: () -> Unit,
    onGradientColor1: () -> Unit,
    onGradientColor2: () -> Unit,
    onAngleChange: (Float) -> Unit,
    onPresetSelected: (Float) -> Unit,
    appearanceAlpha: Float,
    onLayoutEvent: (EditorEvent) -> Unit,
    onSubTabActiveChanged: (Boolean) -> Unit,
) {
    var activeSubTab by rememberSaveable { mutableStateOf<FillSubTab?>(null) }
    var isEditingGradientDetail by rememberSaveable { mutableStateOf(false) }
    var gradientDetailTab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(activeSubTab) {
        onSubTabActiveChanged(activeSubTab != null)
        if (activeSubTab != FillSubTab.COLOR) {
            isEditingGradientDetail = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Horizontal sub-tab bar ALWAYS visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubTabButton(
                icon = Icons.Filled.Palette,
                label = stringResource(R.string.studio_label_color_presets_tab),
                selected = activeSubTab == FillSubTab.PRESETS,
                tokens = tokens,
                onClick = {
                    activeSubTab = if (activeSubTab == FillSubTab.PRESETS) null else FillSubTab.PRESETS
                }
            )
            SubTabButton(
                icon = Icons.Filled.FormatColorFill,
                label = stringResource(R.string.studio_label_bg_color_tab),
                selected = activeSubTab == FillSubTab.COLOR,
                tokens = tokens,
                onClick = {
                    activeSubTab = if (activeSubTab == FillSubTab.COLOR) null else FillSubTab.COLOR
                }
            )
            SubTabButton(
                icon = Icons.Filled.Opacity,
                label = stringResource(R.string.studio_label_opacity_tab),
                selected = activeSubTab == FillSubTab.OPACITY,
                tokens = tokens,
                onClick = {
                    activeSubTab = if (activeSubTab == FillSubTab.OPACITY) null else FillSubTab.OPACITY
                }
            )
        }

        // Sub-tab Content goes BELOW the tab bar
        androidx.compose.animation.AnimatedVisibility(
            visible = activeSubTab != null,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                when (activeSubTab) {
                    FillSubTab.PRESETS -> {
                        ShapeStyleGallery(
                            events = ShapeStyleEvents(
                                updateShapeColor = { onSolidColor(it) },
                                updateStrokeColor = { onLayoutEvent(EditorEvent.UpdateStrokeColor(it)) },
                                updateStrokeWidth = { onLayoutEvent(EditorEvent.UpdateStrokeWidth(it)) },
                                updateShadow = { onLayoutEvent(EditorEvent.UpdateShadow(it)) },
                            ),
                            currentFillArgb = solidArgb,
                        )
                    }
                    FillSubTab.COLOR -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!isEditingGradientDetail) {
                                ModeToggleRow(
                                    solidLabel = stringResource(R.string.studio_label_color_solid),
                                    gradientLabel = stringResource(R.string.studio_label_color_gradient),
                                    isGradient = mode == FillColorMode.Gradient,
                                    tokens = tokens,
                                    onSolid = { onModeChange(FillColorMode.Solid) },
                                    onGradient = {
                                        onModeChange(FillColorMode.Gradient)
                                        isEditingGradientDetail = true
                                    },
                                )

                                if (mode == FillColorMode.Solid) {
                                    LabelColorRow(
                                        currentArgb = solidArgb,
                                        palette = fillPalette,
                                        onSelectColor = onSolidColor,
                                        onCustomColorClick = onCustomSolid,
                                        tokens = tokens,
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        isEditingGradientDetail = true
                                    }
                                }
                            } else {
                                // Sub-navigation inside gradient editor
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { isEditingGradientDetail = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = stringResource(R.string.studio_back_button),
                                            tint = tokens.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.studio_label_gradient_colors_tab),
                                        fontSize = 12.sp,
                                        fontWeight = if (gradientDetailTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (gradientDetailTab == 0) tokens.accent else tokens.textSecondary,
                                        modifier = Modifier.clickable { gradientDetailTab = 0 }
                                    )
                                    Text(
                                        text = stringResource(R.string.studio_label_gradient_direction_tab),
                                        fontSize = 12.sp,
                                        fontWeight = if (gradientDetailTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (gradientDetailTab == 1) tokens.accent else tokens.textSecondary,
                                        modifier = Modifier.clickable { gradientDetailTab = 1 }
                                    )
                                    Text(
                                        text = stringResource(R.string.studio_label_gradient_angle_tab),
                                        fontSize = 12.sp,
                                        fontWeight = if (gradientDetailTab == 2) FontWeight.Bold else FontWeight.Medium,
                                        color = if (gradientDetailTab == 2) tokens.accent else tokens.textSecondary,
                                        modifier = Modifier.clickable { gradientDetailTab = 2 }
                                    )
                                }

                                when (gradientDetailTab) {
                                    0 -> {
                                        GradientEditorSection(
                                            color1Argb = color1,
                                            color2Argb = color2,
                                            stop1Offset = stop1Offset,
                                            stop2Offset = stop2Offset,
                                            angle = angle,
                                            tokens = tokens,
                                            onGradientChanged = { gradient ->
                                                onLayoutEvent(EditorEvent.UpdateFillGradient(gradient))
                                            },
                                        )
                                    }
                                    1 -> {
                                        GradientPresetsGallery(
                                            currentAngle = angle,
                                            tokens = tokens,
                                            onPresetSelected = onPresetSelected,
                                        )
                                    }
                                    2 -> {
                                        Column(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            PrecisionSlider(
                                                label = stringResource(R.string.studio_label_gradient_angle),
                                                value = angle,
                                                valueRange = 0f..359f,
                                                onValueChange = onAngleChange,
                                                valueFormatter = { "${it.toInt()} độ" },
                                                colors = sliderColors,
                                                isCompact = true,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FillSubTab.OPACITY -> {
                        val transparencyPct = ((1f - appearanceAlpha.coerceIn(0f, 1f)) * 100f)
                            .toInt()
                            .coerceIn(0, 100)
                        PrecisionSlider(
                            label = stringResource(R.string.studio_label_opacity_tab),
                            value = transparencyPct.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = { pct ->
                                val alpha = ((100f - pct) / 100f).coerceIn(0.1f, 1f)
                                onLayoutEvent(EditorEvent.UpdateAlpha(alpha))
                            },
                            valueFormatter = { "${it.toInt()}%" },
                            colors = sliderColors,
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun TextColorEditor(
    mode: TextColorMode,
    tokens: EditorTokens,
    solidArgb: Int,
    color1: Int,
    color2: Int,
    angle: Float,
    sliderColors: PrecisionSliderColors,
    onModeChange: (TextColorMode) -> Unit,
    onSolidColor: (Int) -> Unit,
    onCustomSolid: () -> Unit,
    onGradientColor1: () -> Unit,
    onGradientColor2: () -> Unit,
    onAngleChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = stringResource(R.string.studio_label_text_color),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
        )
        ModeToggleRow(
            solidLabel = stringResource(R.string.studio_label_color_solid),
            gradientLabel = stringResource(R.string.studio_label_color_gradient),
            isGradient = mode == TextColorMode.Gradient,
            tokens = tokens,
            onSolid = { onModeChange(TextColorMode.Solid) },
            onGradient = { onModeChange(TextColorMode.Gradient) },
        )
        if (mode == TextColorMode.Solid) {
            LabelColorRow(
                currentArgb = solidArgb,
                palette = textPalette,
                onSelectColor = onSolidColor,
                onCustomColorClick = onCustomSolid,
                tokens = tokens,
            )
        } else {
            GradientStopsRow(
                color1 = Color(color1),
                color2 = Color(color2),
                tokens = tokens,
                onPickColor1 = onGradientColor1,
                onPickColor2 = onGradientColor2,
            )
            PrecisionSlider(
                label = stringResource(R.string.studio_label_gradient_angle),
                value = angle,
                valueRange = 0f..359f,
                onValueChange = onAngleChange,
                valueFormatter = { "${it.toInt()} độ" },
                colors = sliderColors,
                isCompact = true,
            )
        }
    }
}

@Composable
private fun ModeToggleRow(
    solidLabel: String,
    gradientLabel: String,
    isGradient: Boolean,
    tokens: EditorTokens,
    onSolid: () -> Unit,
    onGradient: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(tokens.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LabelChip(
            label = solidLabel,
            selected = !isGradient,
            tokens = tokens,
            onClick = onSolid,
        )
        LabelChip(
            label = gradientLabel,
            selected = isGradient,
            tokens = tokens,
            onClick = onGradient,
        )
    }
}

@Composable
private fun GradientStopsRow(
    color1: Color,
    color2: Color,
    tokens: EditorTokens,
    onPickColor1: () -> Unit,
    onPickColor2: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientStopSwatch(
            label = stringResource(R.string.studio_label_gradient_start),
            color = color1,
            tokens = tokens,
            onClick = onPickColor1,
        )
        GradientStopSwatch(
            label = stringResource(R.string.studio_label_gradient_end),
            color = color2,
            tokens = tokens,
            onClick = onPickColor2,
        )
    }
}

@Composable
private fun GradientStopSwatch(
    label: String,
    color: Color,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(color)
                .border(BorderStroke(0.5.dp, tokens.borderSubtle), CircleShape)
                .clickable(onClick = onClick),
        )
        Text(label, fontSize = 11.sp, color = tokens.textPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun LabelChip(
    label: String,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tokens.accentSoft else Color(0xFFF5F5F5))
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) tokens.accent else tokens.borderSubtle,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) tokens.accent else tokens.textSecondary,
        )
    }
}

private val fillPalette = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1),
    Color(0xFF6D4C41), Color(0xFF212121), Color(0xFFFFFFFF),
)

private val textPalette = listOf(
    Color(0xFFFFFFFF), Color(0xFF212121), Color(0xFFE53935),
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41),
)

@Composable
private fun GradientPresetsGallery(
    currentAngle: Float,
    tokens: EditorTokens,
    onPresetSelected: (Float) -> Unit,
) {
    val presets = remember {
        listOf(
            "→" to 0f,
            "↓" to 90f,
            "←" to 180f,
            "↑" to 270f,
            "↘" to 45f,
            "↗" to 315f
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.studio_label_gradient_direction_tab),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { (label, presetAngle) ->
                val isSelected = abs(currentAngle - presetAngle) < 2f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) tokens.accentSoft else Color(0xFFF5F5F5))
                        .border(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) tokens.accent else Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onPresetSelected(presetAngle) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) tokens.accent else tokens.textPrimary,
                    )
                }
            }
        }
    }
}

