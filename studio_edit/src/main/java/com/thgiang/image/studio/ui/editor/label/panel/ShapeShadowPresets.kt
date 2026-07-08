@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType
import kotlin.math.cos
import kotlin.math.sin

data class ShadowPreset(
    @StringRes val labelRes: Int,
    val intensity: Float,
    val angle: Float,
    val distance: Float,
    val blur: Float? = null,
)

val shadowPresets = listOf(
    ShadowPreset(R.string.studio_shadow_preset_none, 0f, 0f, 0f),
    ShadowPreset(R.string.studio_shadow_preset_light, 0.2f, 45f, 4f),
    ShadowPreset(R.string.studio_shadow_preset_soft, 0.4f, 45f, 8f),
    ShadowPreset(R.string.studio_shadow_preset_medium, 0.5f, 135f, 12f),
    ShadowPreset(R.string.studio_shadow_preset_strong, 0.75f, 135f, 16f),
    ShadowPreset(R.string.studio_shadow_preset_bottom_right, 0.4f, 45f, 10f),
    ShadowPreset(R.string.studio_shadow_preset_bottom, 0.4f, 90f, 10f),
    ShadowPreset(R.string.studio_shadow_preset_center_glow, 0.4f, 0f, 0f, 16f),
)

val shadowColorPalette = listOf(
    Color(0xFF000000), Color(0xFF424242), Color(0xFF757575), Color(0xFFBDBDBD),
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00)
)

@Composable
fun ShapeShadowSection(
    appearance: EditorAppearance,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val displayShadowColorArgb = appearance.shadowColorArgb

    if (showColorPicker) {
        ColorPickerDialog(
            show = true,
            type = ColorPickerType.Circle(showAlphaBar = false),
            onDismissRequest = { showColorPicker = false },
            onPickedColor = { picked ->
                onLayoutEvent(EditorEvent.UpdateShadowColor(picked.toArgb()))
                showColorPicker = false
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Shadow Presets Gallery
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shadowPresets.forEach { preset ->
                val isSelected = if (preset.intensity == 0f) {
                    appearance.shadowIntensity <= 0.05f
                } else {
                    appearance.shadowIntensity > 0.05f &&
                            kotlin.math.abs(appearance.shadowIntensity - preset.intensity) < 0.05f &&
                            kotlin.math.abs(appearance.shadowAngle - preset.angle) < 5f &&
                            kotlin.math.abs(appearance.shadowDistance - preset.distance) < 2f
                }

                ShadowPresetCard(
                    preset = preset,
                    selected = isSelected,
                    onClick = {
                        onLayoutEvent(EditorEvent.UpdateShadow(preset.intensity))
                        onLayoutEvent(EditorEvent.UpdateShadowAngle(preset.angle))
                        onLayoutEvent(EditorEvent.UpdateShadowDistance(preset.distance))
                        onLayoutEvent(EditorEvent.UpdateShadowBlur(preset.blur))
                    },
                    tokens = tokens,
                )
            }
        }

        if (appearance.shadowIntensity > 0.05f) {
            // Intensity Slider
            PrecisionSlider(
                label = stringResource(R.string.studio_shadow_tab_intensity),
                value = appearance.shadowIntensity,
                valueRange = 0f..1f,
                onValueChange = { onLayoutEvent(EditorEvent.UpdateShadow(it)) },
                valueFormatter = { "${(it * 100).toInt()}%" },
                colors = remember(tokens) { tokens.toSliderColors() },
            )

            // Blur Slider
            val currentBlur = appearance.shadowBlur ?: 8f // fallback static default if null
            PrecisionSlider(
                label = stringResource(R.string.studio_elevation_shadow_blur),
                value = currentBlur,
                valueRange = 0f..50f,
                onValueChange = { onLayoutEvent(EditorEvent.UpdateShadowBlur(it)) },
                valueFormatter = { "${it.toInt()}px" },
                colors = remember(tokens) { tokens.toSliderColors() },
            )

            // Shadow Color Row
            Text(
                text = stringResource(R.string.studio_shadow_tab_color),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
            )
            LabelColorRow(
                currentArgb = displayShadowColorArgb,
                palette = shadowColorPalette,
                onSelectColor = { onLayoutEvent(EditorEvent.UpdateShadowColor(it)) },
                onCustomColorClick = { showColorPicker = true },
                tokens = tokens,
            )
        }
    }
}

@Composable
private fun ShadowPresetCard(
    preset: ShadowPreset,
    selected: Boolean,
    onClick: () -> Unit,
    tokens: EditorTokens,
) {
    val borderColor = if (selected) tokens.accent else tokens.borderSubtle
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val cardBg = if (selected) tokens.accentSoft else Color(0xFFF8F8F8)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .height(24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = 5.dp.toPx()

                if (preset.intensity == 0f) {
                    drawCircle(
                        Color(0xFF9CA3AF), r, Offset(cx, cy),
                        style = Stroke(1.2.dp.toPx()),
                    )
                    val d = r * 0.5f
                    drawLine(Color(0xFF9CA3AF), Offset(cx - d, cy - d), Offset(cx + d, cy + d), 1.2.dp.toPx())
                    drawLine(Color(0xFF9CA3AF), Offset(cx + d, cy - d), Offset(cx - d, cy + d), 1.2.dp.toPx())
                } else {
                    val angleRad = Math.toRadians(preset.angle.toDouble())
                    val dist = preset.distance * 0.6f
                    val sx = cos(angleRad).toFloat() * dist
                    val sy = sin(angleRad).toFloat() * dist

                    val shadowBrush = Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = preset.intensity * 0.8f), Color.Transparent),
                        center = Offset(cx + sx, cy + sy),
                        radius = r * 1.8f,
                    )
                    drawCircle(
                        brush = shadowBrush,
                        radius = r * 1.8f,
                        center = Offset(cx + sx, cy + sy),
                    )

                    val sphereBrush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFF8A939E), Color(0xFF3A424A)),
                        center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                        radius = r,
                    )
                    drawCircle(
                        brush = sphereBrush,
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(preset.labelRes),
            color = if (selected) tokens.textPrimary else tokens.textSecondary,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}
