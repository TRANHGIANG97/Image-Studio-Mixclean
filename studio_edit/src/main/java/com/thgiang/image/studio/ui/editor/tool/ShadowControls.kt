@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.tool
import com.thgiang.image.studio.ui.editor.tool.*

import androidx.compose.animation.AnimatedVisibility
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ShadowSubTab(val labelResId: Int) {
    Intensity(R.string.studio_shadow_tab_intensity),
    Angle(R.string.studio_shadow_tab_angle),
    Distance(R.string.studio_shadow_tab_distance),
    Color(R.string.studio_shadow_tab_color),
}

data class ShadowPresetData(
    val key: String,
    val labelResId: Int,
    val intensity: Float,
    val angle: Float,
    val distance: Float,
)

private val ShadowPresets = listOf(
    ShadowPresetData("none", R.string.studio_shadow_preset_none, 0f, 0f, 0f),
    ShadowPresetData("light", R.string.studio_shadow_preset_light, 0.25f, 135f, 8f),
    ShadowPresetData("medium", R.string.studio_shadow_preset_medium, 0.50f, 135f, 15f),
    ShadowPresetData("strong", R.string.studio_shadow_preset_strong, 0.75f, 135f, 24f)
)

private fun presetMatches(preset: ShadowPresetData, appearance: EditorAppearance): Boolean =
    abs(appearance.shadowIntensity - preset.intensity) < 0.01f &&
        abs(appearance.shadowAngle - preset.angle) < 1f &&
        abs(appearance.shadowDistance - preset.distance) < 0.5f

@Composable
fun ShadowToolPanel(
    appearance: EditorAppearance,
    onUpdateShadow: (Float) -> Unit,
    onUpdateShadowAngle: (Float) -> Unit,
    onUpdateShadowDistance: (Float) -> Unit,
    onUpdateShadowColor: (Int) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Row - clickable to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.studio_tool_shadow),
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
            Text(
                text = "${(appearance.shadowIntensity * 100).roundToInt()}%",
                color = tokens.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(180))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Slider Row - Chia đôi chiều ngang (nhỏ lại gấp đôi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "0%",
                            color = tokens.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Slider(
                            value = appearance.shadowIntensity,
                            onValueChange = onUpdateShadow,
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = tokens.textPrimary.copy(alpha = 0.9f),
                                inactiveTrackColor = Color(0xFFE5E7EB)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "100%",
                            color = tokens.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.weight(0.5f))
                }

                // Presets Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShadowPresets.forEach { preset ->
                        ShadowPresetCard(
                            preset = preset,
                            selected = presetMatches(preset, appearance),
                            onClick = {
                                onUpdateShadow(preset.intensity)
                                onUpdateShadowAngle(preset.angle)
                                onUpdateShadowDistance(preset.distance)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun ShadowPresetCard(
    preset: ShadowPresetData,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    val borderColor = if (selected) tokens.accent else tokens.borderSubtle
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val cardBg = if (selected) tokens.accentSoft else Color(0xFFF8F8F8)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = 5.dp.toPx()

                if (preset.key == "none") {
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
            text = stringResource(preset.labelResId),
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
