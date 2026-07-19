package com.thgiang.image.studio.ui.editor.model

import com.thgiang.image.core.util.processors.OpaqueContentBounds
import kotlin.math.min

/**
 * Selection / hit-test metrics for an IMAGE layer with transparent PNG padding.
 * Sizes are in template logical units (same space as [EditorLayer.shapeWidthPx]).
 */
data class ImageLayerTightMetrics(
    val contentWidth: Float,
    val contentHeight: Float,
    /** Offset from [EditorViewport.offset] to the tight content center (template px). */
    val centerOffsetX: Float,
    val centerOffsetY: Float,
)

fun EditorLayer.withOpaqueContentBounds(bounds: OpaqueContentBounds?): EditorLayer {
    if (bounds == null) {
        return if (
            opaqueContentLeftPx == 0 &&
            opaqueContentTopPx == 0 &&
            opaqueContentWidthPx == 0 &&
            opaqueContentHeightPx == 0
        ) {
            this
        } else {
            copy(
                opaqueContentLeftPx = 0,
                opaqueContentTopPx = 0,
                opaqueContentWidthPx = 0,
                opaqueContentHeightPx = 0,
            )
        }
    }
    return copy(
        opaqueContentLeftPx = bounds.left,
        opaqueContentTopPx = bounds.top,
        opaqueContentWidthPx = bounds.width,
        opaqueContentHeightPx = bounds.height,
    )
}

fun EditorLayer.imageLayerTightMetrics(): ImageLayerTightMetrics? {
    if (type != LayerType.IMAGE) return null

    val bitmapW = product.baseWidth.coerceAtLeast(1)
    val bitmapH = product.baseHeight.coerceAtLeast(1)
    val alphaW = opaqueContentWidthPx.takeIf { it > 0 } ?: bitmapW
    val alphaH = opaqueContentHeightPx.takeIf { it > 0 } ?: bitmapH
    if (opaqueContentLeftPx == 0 && opaqueContentTopPx == 0 &&
        alphaW >= bitmapW && alphaH >= bitmapH
    ) {
        return null
    }

    val cropBox = cropRatio.calculateSize(shapeWidthPx, shapeHeightPx)
    val drawW = cropBox.width.toFloat().coerceAtLeast(1f)
    val drawH = cropBox.height.toFloat().coerceAtLeast(1f)
    val fitScale = min(drawW / bitmapW, drawH / bitmapH)

    val tightW = alphaW * fitScale
    val tightH = alphaH * fitScale
    val alphaCenterX = opaqueContentLeftPx + alphaW / 2f
    val alphaCenterY = opaqueContentTopPx + alphaH / 2f
    val offsetX = (alphaCenterX - bitmapW / 2f) * fitScale
    val offsetY = (alphaCenterY - bitmapH / 2f) * fitScale

    return ImageLayerTightMetrics(
        contentWidth = tightW.coerceAtLeast(1f),
        contentHeight = tightH.coerceAtLeast(1f),
        centerOffsetX = offsetX,
        centerOffsetY = offsetY,
    )
}

fun EditorLayer.imageSelectionCenterOffset(): Pair<Float, Float> {
    val tight = imageLayerTightMetrics()
    val scale = viewport.scale
    return (viewport.offset.x + (tight?.centerOffsetX ?: 0f) * scale) to
        (viewport.offset.y + (tight?.centerOffsetY ?: 0f) * scale)
}
