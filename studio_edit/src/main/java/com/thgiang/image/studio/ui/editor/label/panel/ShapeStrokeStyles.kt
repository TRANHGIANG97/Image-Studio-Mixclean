@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R

data class DashStylePreset(
    @StringRes val labelRes: Int,
    val dashArray: List<Float>?,  // null = solid
)

val dashStylePresets: List<DashStylePreset> = listOf(
    DashStylePreset(R.string.studio_dash_solid, null),
    DashStylePreset(R.string.studio_dash_round_dot, listOf(2f, 6f)),
    DashStylePreset(R.string.studio_dash_square_dot, listOf(4f, 4f)),
    DashStylePreset(R.string.studio_dash_dash, listOf(10f, 6f)),
    DashStylePreset(R.string.studio_dash_dash_dot, listOf(12f, 6f, 2f, 6f)),
    DashStylePreset(R.string.studio_dash_dash_dot_dot, listOf(12f, 6f, 2f, 6f, 2f, 6f)),
)

val outlineWeightPresets: List<Float> = listOf(1f, 2f, 3f, 5f)

@Composable
fun DashStylePicker(
    currentDashArray: List<Float>?,
    onDashSelected: (List<Float>?) -> Unit,
    tokens: com.thgiang.image.studio.ui.editor.theme.EditorTokens,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dashStylePresets.forEach { preset ->
            val isSolid = preset.dashArray == null && currentDashArray.isNullOrEmpty()
            val isMatch = preset.dashArray == currentDashArray || isSolid
            val label = stringResource(preset.labelRes)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isMatch) tokens.accentSoft else Color(0xFFF5F5F5))
                    .border(
                        width = if (isMatch) 1.dp else 0.5.dp,
                        color = if (isMatch) tokens.accent else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onDashSelected(preset.dashArray) }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (preset.dashArray != null) {
                        val dashPathEffect = PathEffect.dashPathEffect(
                            preset.dashArray.toFloatArray(), 0f
                        )
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(14.dp)
                                .drawBehind {
                                    drawLine(
                                        color = Color(0xFF424242),
                                        start = Offset(0f, size.height / 2f),
                                        end = Offset(size.width, size.height / 2f),
                                        strokeWidth = 3f,
                                        pathEffect = dashPathEffect,
                                    )
                                },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(14.dp)
                                .drawBehind {
                                    drawLine(
                                        color = Color(0xFF212121),
                                        start = Offset(0f, size.height / 2f),
                                        end = Offset(size.width, size.height / 2f),
                                        strokeWidth = 3f,
                                    )
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OutlineWeightChips(
    currentWeightPx: Float,
    onWeightSelected: (Float) -> Unit,
    tokens: com.thgiang.image.studio.ui.editor.theme.EditorTokens,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        outlineWeightPresets.forEach { weight ->
            val isSelected = kotlin.math.abs(currentWeightPx - weight) < 0.01f
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) tokens.accentSoft else Color(0xFFF5F5F5))
                    .clickable { onWeightSelected(weight) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${weight.toInt()}px",
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) tokens.accent else Color(0xFF616161),
                )
            }
        }
    }
}
