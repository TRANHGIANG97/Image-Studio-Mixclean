package com.thgiang.image.studio.ui.editor.model

import kotlin.math.abs

/** Bakes [EditorViewport.scale] into layer dimensions so UI sliders match visual size. */
object LayerViewportScale {
    private const val SCALE_EPSILON = 0.001f

    fun effectiveTextSizeSp(layer: EditorLayer): Float =
        if (layer.isLabelLayer) layer.textSizeSp * layer.viewport.scale else layer.textSizeSp

    fun needsBake(layer: EditorLayer): Boolean =
        abs(layer.viewport.scale - 1f) >= SCALE_EPSILON &&
            (layer.isLabelLayer || layer.isFrameLayer)

    fun bake(layer: EditorLayer): EditorLayer {
        val scale = layer.viewport.scale
        if (abs(scale - 1f) < SCALE_EPSILON) return layer

        return when {
            layer.isLabelLayer -> layer.copy(
                textSizeSp = (layer.textSizeSp * scale).coerceIn(1f, 3000f),
                shapeWidthPx = (layer.shapeWidthPx * scale).coerceAtLeast(60f),
                shapeHeightPx = (layer.shapeHeightPx * scale).coerceAtLeast(30f),
                viewport = layer.viewport.withScale(1f),
            )
            layer.isFrameLayer -> layer.copy(
                shapeWidthPx = (layer.shapeWidthPx * scale).coerceAtLeast(60f),
                shapeHeightPx = (layer.shapeHeightPx * scale).coerceAtLeast(30f),
                viewport = layer.viewport.withScale(1f),
            )
            else -> layer
        }
    }
}
