package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

object EditorShadowMapper {

    fun configureDropShadowPaint(appearance: EditorAppearance, layerAlpha: Float = 1f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = appearance.shadowColorArgb
            alpha = (
                shadowOpacityFromIntensity(appearance.shadowIntensity) *
                    appearance.alpha *
                    layerAlpha *
                    255f
                ).toInt().coerceIn(0, 255)
            maskFilter = BlurMaskFilter(
                appearance.resolvedShadowBlurRadius().coerceAtLeast(0f),
                BlurMaskFilter.Blur.NORMAL,
            )
        }

    fun shadowOffsetPx(appearance: EditorAppearance, scale: Float = 1f): Pair<Float, Float> {
        val (dx, dy) = shadowOffset(appearance.shadowAngle, appearance.shadowDistance)
        return dx * scale to dy * scale
    }

    fun DrawScope.drawShapeDropShadow(
        shapeType: ShapeType,
        appearance: EditorAppearance,
        scale: Float,
        cornerRadiusX: Float?,
        cornerRadiusY: Float?,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
    ) {
        if (appearance.shadowIntensity <= 0.05f) return
        val paint = configureDropShadowPaint(appearance)
        val (dx, dy) = shadowOffsetPx(appearance, scale)

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.translate(dx, dy)
            nativeCanvas.drawShapeGeometry(
                shapeType = shapeType,
                left = 0f,
                top = 0f,
                shapeW = size.width,
                shapeH = size.height,
                cornerRadiusX = cornerRadiusX,
                cornerRadiusY = cornerRadiusY,
                paint = paint,
                pathData = pathData,
                polygonPoints = polygonPoints,
            )
            nativeCanvas.restore()
        }
    }
}
