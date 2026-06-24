@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.tool.*
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.panel.*

import android.annotation.SuppressLint
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

internal enum class LabelShapeSubTab(val titleRes: Int) {
    CORNER(R.string.studio_label_corner_radius),
    STROKE_COLOR(R.string.studio_label_stroke_color),
    STROKE_WIDTH(R.string.studio_label_stroke_width),
    SHADOW(R.string.studio_label_open_shadow),
}

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
internal fun LabelShapeSection(
    layer: EditorLayer,
    editableShapes: List<ShapeType>,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    var activeSubTab by rememberSaveable { mutableStateOf(LabelShapeSubTab.CORNER) }

    val sliderColors = remember(tokens) { tokens.toSliderColors() }
    val strokeWidthSliderColors = remember(tokens) {
        tokens.toSliderColors().copy(
            thumbColor = Color(0xFF212121),
            thumbGlowColor = Color(0xFF424242),
        )
    }

    val hasShapeBorderColor = layer.strokeColorArgb != null
    val supportsCornerRadius = layer.shapeType == ShapeType.CARD || layer.shapeType == ShapeType.PILL
    val cornerRadius = if (supportsCornerRadius) {
        layer.cornerRadiusX ?: layer.cornerRadiusY ?: 0f
    } else {
        0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShapeGallery(
            shapes = editableShapes,
            selectedShape = layer.shapeType,
            tokens = tokens,
            onShapeSelected = { onLayoutEvent(EditorEvent.UpdateShapeType(it)) },
            singleRow = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelShapeSubTab.entries.forEach { tab ->
                val enabled = when (tab) {
                    LabelShapeSubTab.STROKE_WIDTH -> hasShapeBorderColor
                    LabelShapeSubTab.SHADOW -> layer.shapeType != ShapeType.TEXT_ONLY
                    else -> true
                }
                val isSelected = activeSubTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                !enabled -> Color(0xFFFAFAFA)
                                isSelected -> tokens.accentSoft
                                else -> Color(0xFFF5F5F5)
                            },
                        )
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { activeSubTab = tab },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(tab.titleRes),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            !enabled -> tokens.textSecondary.copy(alpha = 0.45f)
                            isSelected -> tokens.accent
                            else -> tokens.textSecondary
                        },
                    )
                }
            }
        }

        when (activeSubTab) {
            LabelShapeSubTab.CORNER -> {
                if (supportsCornerRadius) {
                    PrecisionSlider(
                        label = stringResource(R.string.studio_label_corner_radius),
                        value = cornerRadius,
                        valueRange = 0f..100f,
                        onValueChange = { onLayoutEvent(EditorEvent.UpdateCornerRadius(it)) },
                        valueFormatter = { "${it.toInt()}px" },
                        colors = sliderColors,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.studio_label_corner_not_applicable),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            LabelShapeSubTab.STROKE_COLOR -> {
                LabelStrokeColorSection(
                    layer = layer,
                    tokens = tokens,
                    onLayoutEvent = onLayoutEvent,
                )
            }
            LabelShapeSubTab.STROKE_WIDTH -> {
                if (hasShapeBorderColor) {
                    LabelStrokeWidthSection(
                        layer = layer,
                        sliderColors = strokeWidthSliderColors,
                        onLayoutEvent = onLayoutEvent,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.studio_label_stroke_width_requires_color),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            LabelShapeSubTab.SHADOW -> {
                if (layer.shapeType == ShapeType.TEXT_ONLY) {
                    Text(
                        text = stringResource(R.string.studio_label_shadow_requires_shape),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                } else {
                    ShadowToolPanel(
                        appearance = layer.appearance,
                        onUpdateShadow = { onLayoutEvent(EditorEvent.UpdateShadow(it)) },
                        onUpdateShadowAngle = { onLayoutEvent(EditorEvent.UpdateShadowAngle(it)) },
                        onUpdateShadowDistance = { onLayoutEvent(EditorEvent.UpdateShadowDistance(it)) },
                        onUpdateShadowColor = { onLayoutEvent(EditorEvent.UpdateShadowColor(it)) },
                        tokens = tokens,
                    )
                }
            }
        }
    }
}
