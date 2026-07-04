@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
internal fun LabelShapeSection(
    layer: EditorLayer,
    editableShapes: List<ShapeType>,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    val sliderColors = remember(tokens) { tokens.toSliderColors() }
    val supportsCornerRadius = layer.shapeType == ShapeType.CARD || layer.shapeType == ShapeType.PILL
    val cornerRadius = if (supportsCornerRadius) {
        layer.cornerRadiusX ?: layer.cornerRadiusY ?: 0f
    } else {
        0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShapeGallery(
            shapes = editableShapes,
            selectedShape = layer.shapeType,
            tokens = tokens,
            onShapeSelected = { onLayoutEvent(EditorEvent.UpdateShapeType(it)) },
            singleRow = true,
        )

        if (supportsCornerRadius) {
            PrecisionSlider(
                label = stringResource(R.string.studio_label_corner_radius),
                value = cornerRadius,
                valueRange = 0f..100f,
                onValueChange = { onLayoutEvent(EditorEvent.UpdateCornerRadius(it)) },
                valueFormatter = { "${it.toInt()}px" },
                colors = sliderColors,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
