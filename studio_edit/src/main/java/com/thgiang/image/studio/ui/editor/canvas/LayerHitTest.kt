package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.canvas.*

import com.thgiang.image.studio.ui.editor.model.*

import androidx.compose.ui.unit.IntSize

/**
 * Hit-test context for canvas tap coordinates (screen space).
 */
data class LayerHitTestContext(
    val tapX: Float,
    val tapY: Float,
    val displayWidthPx: Float,
    val displayHeightPx: Float,
    val templateLeftPx: Float,
    val templateTopPx: Float,
    val calculatedScale: Float,
    val selectionHitPaddingPx: Float,
)

object LayerHitTest {

    /**
     * Returns all visible layers under [context.tapX]/[context.tapY], top-most first.
     */
    fun hitLayersAtPoint(
        layers: List<EditorLayer>,
        context: LayerHitTestContext,
    ): List<EditorLayer> {
        val hits = mutableListOf<EditorLayer>()
        for (layer in layers.reversed()) {
            if (!layer.isVisible) continue
            if (isLayerHit(layer, context)) {
                hits += layer
            }
        }
        return hits
    }

    private fun isLayerHit(layer: EditorLayer, context: LayerHitTestContext): Boolean {
        val (objectWidthPx, objectHeightPx) = layerObjectSizePx(layer, context) ?: return false

        val objectCenterX =
            context.templateLeftPx + context.displayWidthPx / 2f +
                (layer.viewport.offset.x * context.calculatedScale)
        val objectCenterY =
            context.templateTopPx + context.displayHeightPx / 2f +
                (layer.viewport.offset.y * context.calculatedScale)

        val dx = context.tapX - objectCenterX
        val dy = context.tapY - objectCenterY
        val angleRad = Math.toRadians(-layer.viewport.rotation.toDouble())
        val rotatedDx = dx * kotlin.math.cos(angleRad) - dy * kotlin.math.sin(angleRad)
        val rotatedDy = dx * kotlin.math.sin(angleRad) + dy * kotlin.math.cos(angleRad)

        return kotlin.math.abs(rotatedDx) <= (objectWidthPx / 2f) &&
            kotlin.math.abs(rotatedDy) <= (objectHeightPx / 2f)
    }

    private fun layerObjectSizePx(
        layer: EditorLayer,
        context: LayerHitTestContext,
    ): IntSize? {
        val padding = context.selectionHitPaddingPx * 2f
        return when (layer.type) {
            LayerType.SHAPE, LayerType.TEXT, LayerType.SHAPE_TEXT -> {
                val scale = layer.viewport.scale * context.calculatedScale
                var w = layer.shapeWidthPx * scale + padding
                var h = layer.shapeHeightPx * scale + padding
                if (layer.isLabelLayer && layer.text.isNotBlank()) {
                    val textSizePx = layer.textSizeSp * scale
                    h = kotlin.math.max(h, textSizePx * 1.4f + padding)
                    val approxCharW = textSizePx * 0.55f
                    val textW = approxCharW * layer.text.length + padding
                    w = kotlin.math.max(w, textW.coerceAtMost(layer.shapeWidthPx * scale + padding * 2f))
                }
                IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
            }

            LayerType.IMAGE -> {
                if (!layer.product.isBackgroundRemoved || layer.product.foregroundUriString.isNullOrBlank()) {
                    return null
                }
                val croppedSize = layer.cropRatio.calculateSize(
                    layer.shapeWidthPx,
                    layer.shapeHeightPx,
                )
                val w =
                    croppedSize.width * layer.viewport.scale * context.calculatedScale +
                        (EditorConfig.BB_PADDING_PX * 2f) + padding
                val h =
                    croppedSize.height * layer.viewport.scale * context.calculatedScale +
                        (EditorConfig.BB_PADDING_PX * 2f) + padding
                IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
            }

            LayerType.SHADOW_REGION -> {
                val w =
                    layer.shapeWidthPx * layer.viewport.scale * context.calculatedScale + padding
                val h =
                    layer.shapeHeightPx * layer.viewport.scale * context.calculatedScale + padding
                IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
            }
        }
    }
}
