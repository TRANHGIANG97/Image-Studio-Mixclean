package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.model.EditorLayer

/**
 * Pure transform math shared by gesture preview (Phase 4) and commit path.
 */
object GestureLayerOps {
    const val MIN_SCALE = 0.05f
    /** High enough that "Thêm ảnh" on large PSD canvases can fill the frame. */
    const val MAX_SCALE = 40f

    fun applyDelta(layer: EditorLayer, delta: GestureDelta): EditorLayer {
        var newViewport = layer.viewport

        if (delta.pan != Offset.Zero) {
            newViewport = newViewport.withOffset(newViewport.offset + delta.pan)
        }

        var updatedLayer = layer
        if (delta.scale != 1f) {
            // Label + image layers share viewport.scale so frame, text, padding and stroke
            // scale as one unit during the gesture; [LayerViewportScale.bake] commits sizes.
            val newScale = (newViewport.scale * delta.scale).coerceIn(MIN_SCALE, MAX_SCALE)
            newViewport = newViewport.withScale(newScale)
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
