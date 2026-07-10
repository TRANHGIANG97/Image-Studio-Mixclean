package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.LayerViewportScale
import com.thgiang.image.studio.ui.editor.model.isLabelLayer

/**
 * Pure transform math shared by gesture preview (Phase 4) and commit path.
 */
object GestureLayerOps {
    const val MIN_SCALE = 0.2f
    const val MAX_SCALE = 5f

    fun applyDelta(layer: EditorLayer, delta: GestureDelta): EditorLayer {
        var newViewport = layer.viewport

        if (delta.pan != Offset.Zero) {
            newViewport = newViewport.withOffset(newViewport.offset + delta.pan)
        }

        var updatedLayer = layer
        if (layer.isLabelLayer) {
            if (delta.scale != 1f) {
                val newTextSize = (layer.textSizeSp * delta.scale)
                    .coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP)
                val newW = (layer.shapeWidthPx * delta.scale).coerceAtLeast(60f)
                val newH = (layer.shapeHeightPx * delta.scale).coerceAtLeast(30f)
                updatedLayer = updatedLayer.copy(
                    textSizeSp = newTextSize,
                    shapeWidthPx = newW,
                    shapeHeightPx = newH,
                )
            }
        } else {
            if (delta.scale != 1f) {
                val newScale = (newViewport.scale * delta.scale).coerceIn(MIN_SCALE, MAX_SCALE)
                newViewport = newViewport.withScale(newScale)
            }
        }

        if (delta.rotation != 0f) {
            var newRotation = (newViewport.rotation + delta.rotation) % 360f
            if (newRotation < 0f) newRotation += 360f
            newViewport = newViewport.withRotation(newRotation)
        }

        updatedLayer = updatedLayer.copy(viewport = newViewport)
        if (delta.deltaWidth != 0f || delta.deltaHeight != 0f) {
            val newW = (updatedLayer.shapeWidthPx + delta.deltaWidth).coerceAtLeast(60f)
            val newH = (updatedLayer.shapeHeightPx + delta.deltaHeight).coerceAtLeast(30f)
            updatedLayer = updatedLayer.copy(shapeWidthPx = newW, shapeHeightPx = newH)
        }
        return updatedLayer
    }
}
