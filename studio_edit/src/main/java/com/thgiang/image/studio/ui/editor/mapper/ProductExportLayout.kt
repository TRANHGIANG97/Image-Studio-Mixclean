package com.thgiang.image.studio.ui.editor.mapper

import com.thgiang.image.studio.ui.editor.model.CropRatio
import kotlin.math.min

/**
 * Export layout for IMAGE/product layers — must match [com.thgiang.image.studio.ui.editor.canvas.ProductLayerV2]:
 * - Draw box = crop window from [CropRatio.calculateSize] on the shape slot
 * - Bitmap uses ContentScale.Fit (uniform scale, centered) inside that box
 *
 * Stretch-to-fill previously used in [com.thgiang.image.studio.ui.editor.EditorRenderer] mismatched
 * preview whenever the sample bitmap aspect differed from the slot (common for unreplaced
 * replaceable products). Replaced products re-fit the slot aspect, so stretch looked correct.
 */
data class ProductExportLayout(
    /** Crop-window top-left in template pixels. */
    val drawX: Float,
    val drawY: Float,
    val drawW: Float,
    val drawH: Float,
    /** Fitted bitmap top-left (ContentScale.Fit) in template pixels. */
    val fittedX: Float,
    val fittedY: Float,
    /** Uniform scale mapping bitmap pixels → template pixels. */
    val fitScale: Float,
) {
    companion object {
        fun compute(
            templateWidth: Int,
            templateHeight: Int,
            shapeWidthPx: Float,
            shapeHeightPx: Float,
            viewportScale: Float,
            offsetX: Float,
            offsetY: Float,
            cropRatio: CropRatio,
            imageWidth: Int,
            imageHeight: Int,
        ): ProductExportLayout {
            val cropBox = cropRatio.calculateSize(
                shapeWidthPx.coerceAtLeast(1f),
                shapeHeightPx.coerceAtLeast(1f),
            )
            val drawW = cropBox.width * viewportScale
            val drawH = cropBox.height * viewportScale
            val drawX = (templateWidth - drawW) / 2f + offsetX
            val drawY = (templateHeight - drawH) / 2f + offsetY

            val fgW = imageWidth.toFloat().coerceAtLeast(1f)
            val fgH = imageHeight.toFloat().coerceAtLeast(1f)
            val fitScale = min(drawW / fgW, drawH / fgH)
            val fittedW = fgW * fitScale
            val fittedH = fgH * fitScale
            val fittedX = drawX + (drawW - fittedW) / 2f
            val fittedY = drawY + (drawH - fittedH) / 2f

            return ProductExportLayout(
                drawX = drawX,
                drawY = drawY,
                drawW = drawW,
                drawH = drawH,
                fittedX = fittedX,
                fittedY = fittedY,
                fitScale = fitScale,
            )
        }
    }
}
