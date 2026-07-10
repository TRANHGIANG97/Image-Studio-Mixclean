package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.thgiang.image.studio.ui.editor.model.CropRatio
import com.thgiang.image.studio.ui.editor.model.EditorLayer

/**
 * Crop pan bounds for interactive crop (Phase 5).
 */
object CropMath {

    fun cropWindowSize(layer: EditorLayer): IntSize =
        layer.cropRatio.calculateSize(layer.shapeWidthPx, layer.shapeHeightPx)

    fun maxPan(layer: EditorLayer): Pair<Float, Float> {
        val crop = cropWindowSize(layer)
        val cropW = crop.width.toFloat()
        val cropH = crop.height.toFloat()
        val imgW = layer.product.baseWidth.toFloat().coerceAtLeast(1f)
        val imgH = layer.product.baseHeight.toFloat().coerceAtLeast(1f)
        val fitScale = minOf(cropW / imgW, cropH / imgH)
        val renderedW = imgW * fitScale
        val renderedH = imgH * fitScale
        val maxX = ((renderedW - cropW) / 2f).coerceAtLeast(0f)
        val maxY = ((renderedH - cropH) / 2f).coerceAtLeast(0f)
        return maxX to maxY
    }

    fun clampOffset(layer: EditorLayer, offsetX: Float, offsetY: Float): Offset {
        val (maxX, maxY) = maxPan(layer)
        return Offset(
            x = offsetX.coerceIn(-maxX, maxX),
            y = offsetY.coerceIn(-maxY, maxY),
        )
    }

    fun applyPanDelta(layer: EditorLayer, delta: Offset): EditorLayer {
        val next = clampOffset(
            layer,
            layer.cropOffsetX + delta.x,
            layer.cropOffsetY + delta.y,
        )
        return layer.copy(cropOffsetX = next.x, cropOffsetY = next.y)
    }

    fun resetOffsetForRatio(layer: EditorLayer, ratio: CropRatio): EditorLayer =
        layer.copy(cropRatio = ratio, cropOffsetX = 0f, cropOffsetY = 0f)
}
