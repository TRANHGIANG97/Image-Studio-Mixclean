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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.resolveShapeDepthColor
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.ElevationTarget
import com.thgiang.image.studio.ui.editor.model.ShapeElevationStyle
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType
import kotlin.math.cos
import kotlin.math.sin

/** Word 3-D Format → Depth presets (Size in pt ≈ px at 1:1). */
data class ElevationPreset(
    val label: String,
    val depthPx: Float,
    val style: ShapeElevationStyle,
    val angle: Float = 225f,
)

val elevationPresets: List<ElevationPreset> = listOf(
    ElevationPreset("Không", 0f, ShapeElevationStyle.RAISED),
    ElevationPreset("Mỏng", 6f, ShapeElevationStyle.RAISED),
    ElevationPreset("Vừa", 15f, ShapeElevationStyle.RAISED),
    ElevationPreset("Sâu", 30f, ShapeElevationStyle.RAISED),
    ElevationPreset("Chìm", 12f, ShapeElevationStyle.INSET, angle = 45f),
)

internal val depthColorPalette = listOf(
    Color(0xFF5C9EBF), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6D4C41),
    Color(0xFF757575), Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF00838F),
)

private enum class ElevationSubTab {
    PRESETS,
    CUSTOM,
    SHADOW_BLUR,
}

@Composable
fun ShapeElevationSection(
    appearance: EditorAppearance,
    fillColorArgb: Int,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    elevationTarget: ElevationTarget = ElevationTarget.SHAPE,
    onSubTabActiveChanged: (Boolean) -> Unit = {},
) {
    var showDepthColorPicker by remember { mutableStateOf(false) }
    var activeSubTab by rememberSaveable { mutableStateOf(ElevationSubTab.PRESETS) }
    val depthPx = appearance.depthSizePx
        ?: (appearance.elevationIntensity * EditorAppearance.MAX_SHAPE_DEPTH_PX)
    val displayDepthColor = resolveShapeDepthColor(fillColorArgb, appearance.depthColorArgb)
    val sliderColors = remember(tokens) { tokens.toSliderColors() }

    val emitElevationEvent: (EditorEvent) -> Unit = { event ->
        if (appearance.elevationTarget != elevationTarget) {
            onLayoutEvent(EditorEvent.UpdateElevationTarget(elevationTarget))
        }
        onLayoutEvent(event)
    }

    LaunchedEffect(elevationTarget) {
        if (appearance.elevationTarget != elevationTarget) {
            onLayoutEvent(EditorEvent.UpdateElevationTarget(elevationTarget))
        }
    }

    LaunchedEffect(activeSubTab, depthPx, appearance.shadowBlur) {
        onSubTabActiveChanged(activeSubTab != ElevationSubTab.PRESETS)
        if (
            activeSubTab == ElevationSubTab.SHADOW_BLUR &&
            depthPx > 0.5f &&
            appearance.shadowBlur == null
        ) {
            emitElevationEvent(EditorEvent.UpdateShadowBlur(8f))
        }
    }

    if (showDepthColorPicker) {
        ColorPickerDialog(
            show = true,
            type = ColorPickerType.Circle(showAlphaBar = false),
            onDismissRequest = { showDepthColorPicker = false },
            onPickedColor = { picked ->
                emitElevationEvent(EditorEvent.UpdateDepthColor(picked.toArgb()))
                showDepthColorPicker = false
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ElevationSubTabRow(
            raisedLabel = stringResource(R.string.studio_elevation_raised),
            insetLabel = stringResource(R.string.studio_elevation_inset),
            customLabel = stringResource(R.string.studio_elevation_custom),
            shadowBlurLabel = stringResource(R.string.studio_elevation_shadow_blur),
            activeSubTab = activeSubTab,
            isInset = appearance.elevationStyle == ShapeElevationStyle.INSET,
            tokens = tokens,
            onSelectPresets = { style ->
                activeSubTab = ElevationSubTab.PRESETS
                emitElevationEvent(EditorEvent.UpdateElevationStyle(style))
            },
            onSelectCustom = { activeSubTab = ElevationSubTab.CUSTOM },
            onSelectShadowBlur = { activeSubTab = ElevationSubTab.SHADOW_BLUR },
        )

        when (activeSubTab) {
            ElevationSubTab.PRESETS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    elevationPresets.forEach { preset ->
                        val isSelected = if (preset.depthPx <= 0f) {
                            depthPx <= 0.5f
                        } else {
                            depthPx > 0.5f &&
                                appearance.elevationStyle == preset.style &&
                                kotlin.math.abs(depthPx - preset.depthPx) < 4f
                        }
                        ElevationPresetCard(
                            preset = preset,
                            selected = isSelected,
                            tokens = tokens,
                            onClick = {
                                emitElevationEvent(EditorEvent.UpdateDepthSize(preset.depthPx))
                                emitElevationEvent(EditorEvent.UpdateElevationStyle(preset.style))
                                emitElevationEvent(EditorEvent.UpdateExtrusionAngle(preset.angle))
                            },
                        )
                    }
                }
            }
            ElevationSubTab.CUSTOM -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.studio_elevation_depth_color),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    LabelColorRow(
                        currentArgb = displayDepthColor,
                        palette = depthColorPalette,
                        onSelectColor = { emitElevationEvent(EditorEvent.UpdateDepthColor(it)) },
                        onCustomColorClick = { showDepthColorPicker = true },
                        tokens = tokens,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PrecisionSlider(
                            label = stringResource(R.string.studio_elevation_depth_size),
                            value = depthPx,
                            valueRange = 0f..EditorAppearance.MAX_SHAPE_DEPTH_PX,
                            onValueChange = { emitElevationEvent(EditorEvent.UpdateDepthSize(it)) },
                            valueFormatter = { "${it.toInt()}px" },
                            colors = sliderColors,
                            isCompact = true,
                        )
                        PrecisionSlider(
                            label = stringResource(R.string.studio_elevation_lighting_angle),
                            value = appearance.extrusionAngle,
                            valueRange = 0f..360f,
                            onValueChange = { emitElevationEvent(EditorEvent.UpdateExtrusionAngle(it)) },
                            valueFormatter = { "${it.toInt()}°" },
                            colors = sliderColors,
                            isCompact = true,
                        )
                    }
                }
            }
            ElevationSubTab.SHADOW_BLUR -> {
                val currentBlur = appearance.shadowBlur ?: 8f
                PrecisionSlider(
                    label = stringResource(R.string.studio_elevation_shadow_blur),
                    value = currentBlur,
                    valueRange = 0f..50f,
                    onValueChange = { emitElevationEvent(EditorEvent.UpdateShadowBlur(it)) },
                    valueFormatter = { "${it.toInt()}px" },
                    colors = sliderColors,
                    isCompact = true,
                )
            }
        }
    }
}

@Composable
private fun ElevationSubTabRow(
    raisedLabel: String,
    insetLabel: String,
    customLabel: String,
    shadowBlurLabel: String,
    activeSubTab: ElevationSubTab,
    isInset: Boolean,
    tokens: EditorTokens,
    onSelectPresets: (ShapeElevationStyle) -> Unit,
    onSelectCustom: () -> Unit,
    onSelectShadowBlur: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElevationSubTabChip(
            label = raisedLabel,
            selected = activeSubTab == ElevationSubTab.PRESETS && !isInset,
            tokens = tokens,
            onClick = { onSelectPresets(ShapeElevationStyle.RAISED) },
        )
        ElevationSubTabChip(
            label = insetLabel,
            selected = activeSubTab == ElevationSubTab.PRESETS && isInset,
            tokens = tokens,
            onClick = { onSelectPresets(ShapeElevationStyle.INSET) },
        )
        ElevationSubTabChip(
            label = customLabel,
            selected = activeSubTab == ElevationSubTab.CUSTOM,
            tokens = tokens,
            onClick = onSelectCustom,
        )
        ElevationSubTabChip(
            label = shadowBlurLabel,
            selected = activeSubTab == ElevationSubTab.SHADOW_BLUR,
            tokens = tokens,
            onClick = onSelectShadowBlur,
        )
    }
}

@Composable
private fun ElevationSubTabChip(
    label: String,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) tokens.accent else tokens.textSecondary,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun ElevationPresetCard(
    preset: ElevationPreset,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) tokens.accent else tokens.borderSubtle
    val cardBg = if (selected) tokens.accentSoft else Color(0xFFF8F8F8)
    val faceColor = Color(0xFF1F6B73)
    val sideColor = Color(0xFF5C9EBF)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .height(24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f + 1f
                val triH = size.height * 0.55f
                val triW = size.width * 0.5f

                if (preset.depthPx <= 0f) {
                    val flat = Path().apply {
                        moveTo(cx, cy - triH / 2f)
                        lineTo(cx + triW / 2f, cy + triH / 2f)
                        lineTo(cx - triW / 2f, cy + triH / 2f)
                        close()
                    }
                    drawPath(flat, faceColor.copy(alpha = 0.35f))
                    drawPath(flat, Color(0xFF9CA3AF), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                } else {
                    val depth = preset.depthPx * 0.22f
                    val angleRad = Math.toRadians(preset.angle.toDouble())
                    val dx = (cos(angleRad) * depth).toFloat()
                    val dy = (sin(angleRad) * depth).toFloat()

                    val top = Offset(cx, cy - triH / 2f)
                    val right = Offset(cx + triW / 2f, cy + triH / 2f)
                    val left = Offset(cx - triW / 2f, cy + triH / 2f)

                    val sidePath = Path().apply {
                        moveTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        lineTo(right.x + dx, right.y + dy)
                        lineTo(left.x + dx, left.y + dy)
                        close()
                    }
                    drawPath(sidePath, sideColor.copy(alpha = 0.85f))

                    val backPath = Path().apply {
                        moveTo(top.x + dx, top.y + dy)
                        lineTo(right.x + dx, right.y + dy)
                        lineTo(left.x + dx, left.y + dy)
                        close()
                    }
                    drawPath(backPath, sideColor.copy(alpha = 0.55f))

                    val frontPath = Path().apply {
                        moveTo(top.x, top.y)
                        lineTo(right.x, right.y)
                        lineTo(left.x, left.y)
                        close()
                    }
                    drawPath(frontPath, faceColor)
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = preset.label,
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
