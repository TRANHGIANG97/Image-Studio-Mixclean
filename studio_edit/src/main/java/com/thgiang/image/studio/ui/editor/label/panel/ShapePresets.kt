@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.canvas.rememberCheckerboardBrush
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.label.model.hasTransparentFill

/**
 * Data class for a Shape Style preset — matches Microsoft Word's "Shape Styles" gallery.
 * Each preset bundles: fill color, outline color, outline width, and optional shadow.
 */
data class ShapeStylePreset(
    @StringRes val labelRes: Int,
    val fillColorArgb: Int,
    val outlineColorArgb: Int,
    val outlineWidthPx: Float,
    val shadowIntensity: Float = 0f,
)

private val shapeColorLabelRes = listOf(
    R.string.studio_color_blue,
    R.string.studio_color_red,
    R.string.studio_color_green,
    R.string.studio_color_orange,
    R.string.studio_color_purple,
    R.string.studio_color_cyan,
    R.string.studio_color_teal,
    R.string.studio_color_indigo,
    R.string.studio_color_pink,
    R.string.studio_color_amber,
    R.string.studio_color_lime,
    R.string.studio_color_deep_purple,
    R.string.studio_color_brown,
    R.string.studio_color_blue_gray,
    R.string.studio_color_gray,
    R.string.studio_color_black,
)

/**
 * 24 Shape Style presets:
 * - 16 "Colored Fill": solid fill + matching outline
 * - 8 "Subtle Effect": light fill + subtle shadow
 */
internal val shapeStylePresets: List<ShapeStylePreset> = buildList {
    val coloredFillData = listOf(
        Triple(0xFFE3F2FD.toInt(), 0xFF1565C0.toInt(), 2f),
        Triple(0xFFFFEBEE.toInt(), 0xFFC62828.toInt(), 2f),
        Triple(0xFFE8F5E9.toInt(), 0xFF2E7D32.toInt(), 2f),
        Triple(0xFFFFF3E0.toInt(), 0xFFEF6C00.toInt(), 2f),
        Triple(0xFFF3E5F5.toInt(), 0xFF7B1FA2.toInt(), 2f),
        Triple(0xFFE0F7FA.toInt(), 0xFF00838F.toInt(), 2f),
        Triple(0xFFE0F2F1.toInt(), 0xFF00695C.toInt(), 2f),
        Triple(0xFFE8EAF6.toInt(), 0xFF283593.toInt(), 2f),
        Triple(0xFFFCE4EC.toInt(), 0xFFAD1457.toInt(), 2f),
        Triple(0xFFFFF8E1.toInt(), 0xFFFF8F00.toInt(), 2f),
        Triple(0xFFF1F8E9.toInt(), 0xFF558B2F.toInt(), 2f),
        Triple(0xFFEDE7F6.toInt(), 0xFF4527A0.toInt(), 2f),
        Triple(0xFFEFEBE9.toInt(), 0xFF4E342E.toInt(), 2f),
        Triple(0xFFECEFF1.toInt(), 0xFF37474F.toInt(), 2f),
        Triple(0xFFF5F5F5.toInt(), 0xFF616161.toInt(), 2f),
        Triple(0xFF212121.toInt(), 0xFFFFFFFF.toInt(), 2f),
    )
    coloredFillData.forEachIndexed { index, (fill, outline, width) ->
        add(ShapeStylePreset(shapeColorLabelRes[index], fill, outline, width))
    }

    val subtleEffectData = listOf(
        Triple(0xFFBBDEFB.toInt(), 0xFF90CAF9.toInt(), 1f),
        Triple(0xFFFFCDD2.toInt(), 0xFFEF9A9A.toInt(), 1f),
        Triple(0xFFC8E6C9.toInt(), 0xFFA5D6A7.toInt(), 1f),
        Triple(0xFFFFE0B2.toInt(), 0xFFFFCC80.toInt(), 1f),
        Triple(0xFFE1BEE7.toInt(), 0xFFCE93D8.toInt(), 1f),
        Triple(0xFFB2EBF2.toInt(), 0xFF80DEEA.toInt(), 1f),
        Triple(0xFFB2DFDB.toInt(), 0xFF80CBC4.toInt(), 1f),
        Triple(0xFFC5CAE9.toInt(), 0xFF9FA8DA.toInt(), 1f),
    )
    subtleEffectData.forEachIndexed { index, (fill, outline, width) ->
        add(ShapeStylePreset(shapeColorLabelRes[index], fill, outline, width, 0.15f))
    }
}

data class ShapeStyleEvents(
    val updateShapeColor: (Int) -> Unit,
    val updateStrokeColor: (Int) -> Unit,
    val updateStrokeWidth: (Float) -> Unit,
    val updateShadow: (Float) -> Unit,
)

/**
 * Canva-style compact preset strip:
 * - Single horizontal scrolling row (LazyRow) — no multi-row grid
 * - 44dp chip size (was 52dp)
 * - Inline "Subtle" divider chip
 * - [currentFillArgb] highlights the active preset with accent border
 */
@Composable
fun ShapeStyleGallery(
    events: ShapeStyleEvents,
    modifier: Modifier = Modifier,
    currentFillArgb: Int = 0,
    showNoneOption: Boolean = true,
) {
    val coloredFill = shapeStylePresets.take(16)
    val subtleEffect = shapeStylePresets.drop(16)
    val listState = rememberLazyListState()

    // Merge into a single flat list with a divider sentinel
    data class RowItem(val preset: ShapeStylePreset?, val isDivider: Boolean = false)

    val allItems = buildList {
        coloredFill.forEach { add(RowItem(it)) }
        add(RowItem(null, isDivider = true))  // separator
        subtleEffect.forEach { add(RowItem(it)) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showNoneOption) {
                item(key = "none_preset") {
                    NoneStyleChip(
                        isSelected = currentFillArgb.hasTransparentFill(),
                        onClick = {
                            events.updateShapeColor(ShapeLabelDefaults.TRANSPARENT_FILL_ARGB)
                            events.updateStrokeColor(0x00000000)
                            events.updateStrokeWidth(0f)
                            events.updateShadow(0f)
                        },
                    )
                }
            }
            items(allItems) { item ->
                if (item.isDivider) {
                    // Inline category separator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                        Text(
                            text = stringResource(R.string.studio_shape_style_subtle),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9E9E9E),
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                    }
                } else {
                    item.preset?.let { preset ->
                        val isSelected = preset.fillColorArgb == currentFillArgb
                        CompactStyleChip(
                            preset = preset,
                            isSelected = isSelected,
                            events = events,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoneStyleChip(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val checkerboard = rememberCheckerboardBrush()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF5F5F5))
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFF1565C0), RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(brush = checkerboard, radius = radius, center = center)
            drawLine(
                color = Color(0xFFE53935),
                start = Offset(size.width * 0.2f, size.height * 0.8f),
                end = Offset(size.width * 0.8f, size.height * 0.2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color(0xFFBDBDBD),
                radius = radius - 0.5.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun CompactStyleChip(
    preset: ShapeStylePreset,
    isSelected: Boolean,
    events: ShapeStyleEvents,
) {
    val fillColor = Color(preset.fillColorArgb)
    val outlineColor = Color(preset.outlineColorArgb)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF5F5F5))
            .then(
                if (isSelected) Modifier.border(2.dp, outlineColor, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                events.updateShapeColor(preset.fillColorArgb)
                events.updateStrokeColor(preset.outlineColorArgb)
                events.updateStrokeWidth(preset.outlineWidthPx)
                events.updateShadow(preset.shadowIntensity)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (preset.shadowIntensity > 0f) {
                        Modifier.shadow(
                            elevation = (preset.shadowIntensity * 8).dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.15f),
                            spotColor = Color.Black.copy(alpha = 0.10f),
                        )
                    } else Modifier
                )
                .background(fillColor, CircleShape)
                .border(
                    width = preset.outlineWidthPx.coerceAtMost(2f).dp,
                    color = outlineColor,
                    shape = CircleShape,
                ),
        )
    }
}
