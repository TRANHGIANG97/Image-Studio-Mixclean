package com.thgiang.image.studio.ui.editor.components.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.CollapsiblePanel
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.core.design.components.PrecisionSliderDefaults
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

// ── Opacity Card ──────────────────────────────────────────────

@Composable
fun OpacityCard(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    CollapsiblePanel(
        title = "Opacity",
        modifier = modifier,
        titleColor = tokens.textPrimary,
        titleSize = 12f,
        contentPadding = PaddingValues(vertical = 4.dp),
        headerPadding = PaddingValues(vertical = 2.dp)
    ) {
        PrecisionSlider(
            label = "Opacity",
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0f..1f,
            valueFormatter = { "${(it * 100).toInt()}%" },
            colors = PrecisionSliderDefaults.colors(
                labelColor = tokens.textPrimary,
                labelActiveColor = tokens.accent,
                valuePillBackground = tokens.surfaceFloating,
                valuePillTextColor = tokens.textPrimary,
                trackColor = tokens.surfaceFloating,
                trackActiveColor = tokens.accent,
                thumbColor = Color.White,
                thumbGlowColor = tokens.accent,
                rangeLabelColor = tokens.textSecondary,
                borderColor = tokens.borderSubtle,
            )
        )
    }
}

// ── Shadow Card ───────────────────────────────────────────────

@Composable
fun ShadowCard(
    intensity: Float,
    angle: Float,
    distance: Float,
    shadowColor: Color,
    onIntensityChange: (Float) -> Unit,
    onAngleChange: (Float) -> Unit,
    onDistanceChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val sliderColors = remember(tokens) {
        PrecisionSliderDefaults.colors(
            labelColor = tokens.textPrimary,
            labelActiveColor = tokens.accent,
            valuePillBackground = tokens.surfaceFloating,
            valuePillTextColor = tokens.textPrimary,
            trackColor = tokens.surfaceFloating,
            trackActiveColor = tokens.accent,
            thumbColor = Color.White,
            thumbGlowColor = tokens.accent,
            rangeLabelColor = tokens.textSecondary,
            borderColor = tokens.borderSubtle,
        )
    }

    CollapsiblePanel(
        title = "Shadow",
        modifier = modifier,
        titleColor = tokens.textPrimary,
        titleSize = 12f,
        contentPadding = PaddingValues(vertical = 4.dp),
        headerPadding = PaddingValues(vertical = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrecisionSlider(
                label = "Intensity",
                value = intensity,
                onValueChange = onIntensityChange,
                valueRange = 0f..1f,
                valueFormatter = { "${(it * 100).toInt()}%" },
                colors = sliderColors
            )

            PrecisionSlider(
                label = "Angle",
                value = angle,
                onValueChange = onAngleChange,
                valueRange = 0f..360f,
                valueFormatter = { "${it.toInt()}°" },
                colors = sliderColors
            )

            PrecisionSlider(
                label = "Distance",
                value = distance,
                onValueChange = onDistanceChange,
                valueRange = 0f..40f,
                valueFormatter = { it.toInt().toString() },
                colors = sliderColors
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Color",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Spacer(Modifier.weight(1f))
                ShadowColorChips(
                    currentColor = shadowColor,
                    onSelectColor = onColorChange,
                    tokens = tokens
                )
            }
        }
    }
}

@Composable
private fun ShadowColorChips(
    currentColor: Color,
    onSelectColor: (Color) -> Unit,
    tokens: EditorTokens
) {
    val presetColors = remember {
        listOf(
            Color.Black,
            Color(0xFF2C2C2C),
            Color(0xFF1E3A8A),
            Color(0xFF14532D),
            Color(0xFF581C87),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presetColors.forEach { color ->
            val isSelected = currentColor == color
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 0.5.dp,
                        color = if (isSelected) tokens.accent else tokens.borderSubtle,
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelectColor(color) }
            )
        }
    }
}

// ── Transform Card ────────────────────────────────────────────

@Composable
fun TransformCard(
    scale: Float,
    rotation: Float,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val sliderColors = remember(tokens) {
        PrecisionSliderDefaults.colors(
            labelColor = tokens.textPrimary,
            labelActiveColor = tokens.accent,
            valuePillBackground = tokens.surfaceFloating,
            valuePillTextColor = tokens.textPrimary,
            trackColor = tokens.surfaceFloating,
            trackActiveColor = tokens.accent,
            thumbColor = Color.White,
            thumbGlowColor = tokens.accent,
            rangeLabelColor = tokens.textSecondary,
            borderColor = tokens.borderSubtle,
        )
    }

    CollapsiblePanel(
        title = "Transform",
        modifier = modifier,
        titleColor = tokens.textPrimary,
        titleSize = 12f,
        contentPadding = PaddingValues(vertical = 4.dp),
        headerPadding = PaddingValues(vertical = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrecisionSlider(
                label = "Scale",
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 0.1f..5f,
                valueFormatter = { "${"%.0f".format(it * 100)}%" },
                colors = sliderColors
            )

            PrecisionSlider(
                label = "Rotation",
                value = rotation,
                onValueChange = onRotationChange,
                valueRange = -180f..180f,
                valueFormatter = { "${it.toInt()}°" },
                colors = sliderColors
            )
        }
    }
}

// ── Adjustment Card ───────────────────────────────────────────

@Composable
fun AdjustmentCard(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val sliderColors = remember(tokens) {
        PrecisionSliderDefaults.colors(
            labelColor = tokens.textPrimary,
            labelActiveColor = tokens.accent,
            valuePillBackground = tokens.surfaceFloating,
            valuePillTextColor = tokens.textPrimary,
            trackColor = tokens.surfaceFloating,
            trackActiveColor = tokens.accent,
            thumbColor = Color.White,
            thumbGlowColor = tokens.accent,
            rangeLabelColor = tokens.textSecondary,
            borderColor = tokens.borderSubtle,
        )
    }

    CollapsiblePanel(
        title = "Adjustments",
        modifier = modifier,
        titleColor = tokens.textPrimary,
        titleSize = 12f,
        contentPadding = PaddingValues(vertical = 4.dp),
        headerPadding = PaddingValues(vertical = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrecisionSlider(
                label = "Brightness",
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -100f..100f,
                valueFormatter = { it.toInt().toString() },
                colors = sliderColors
            )

            PrecisionSlider(
                label = "Contrast",
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = -100f..100f,
                valueFormatter = { it.toInt().toString() },
                colors = sliderColors
            )

            PrecisionSlider(
                label = "Saturation",
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = -100f..100f,
                valueFormatter = { it.toInt().toString() },
                colors = sliderColors
            )
        }
    }
}

// ── Color Overlay Card ────────────────────────────────────────

@Composable
fun ColorOverlayCard(
    overlayColor: Color,
    overlayAlpha: Float,
    onColorChange: (Color) -> Unit,
    onAlphaChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val sliderColors = remember(tokens) {
        PrecisionSliderDefaults.colors(
            labelColor = tokens.textPrimary,
            labelActiveColor = tokens.accent,
            valuePillBackground = tokens.surfaceFloating,
            valuePillTextColor = tokens.textPrimary,
            trackColor = tokens.surfaceFloating,
            trackActiveColor = tokens.accent,
            thumbColor = Color.White,
            thumbGlowColor = tokens.accent,
            rangeLabelColor = tokens.textSecondary,
            borderColor = tokens.borderSubtle,
        )
    }

    CollapsiblePanel(
        title = "Color Overlay",
        modifier = modifier,
        titleColor = tokens.textPrimary,
        titleSize = 12f,
        contentPadding = PaddingValues(vertical = 4.dp),
        headerPadding = PaddingValues(vertical = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadowColorChips(
                currentColor = overlayColor,
                onSelectColor = onColorChange,
                tokens = tokens
            )

            PrecisionSlider(
                label = "Intensity",
                value = overlayAlpha,
                onValueChange = onAlphaChange,
                valueRange = 0f..1f,
                valueFormatter = { "${(it * 100).toInt()}%" },
                colors = sliderColors
            )
        }
    }
}

// ── Blend Mode Card ───────────────────────────────────────────

enum class EditorBlendMode(val label: String) {
    Normal("Normal"),
    Multiply("Multiply"),
    Screen("Screen"),
    Overlay("Overlay"),
    Darken("Darken"),
    Lighten("Lighten"),
    SoftLight("Soft Light"),
    HardLight("Hard Light"),
}

@Composable
fun BlendModeCard(
    currentMode: EditorBlendMode,
    onModeChange: (EditorBlendMode) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    CollapsiblePanel(
        title = "Blend Mode",
        modifier = modifier,
        titleColor = tokens.textPrimary,
        titleSize = 12f,
        contentPadding = PaddingValues(vertical = 4.dp),
        headerPadding = PaddingValues(vertical = 2.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorBlendMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) tokens.accent.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeChange(mode) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) tokens.accent else tokens.textSecondary
                    )
                }
            }
        }
    }
}
