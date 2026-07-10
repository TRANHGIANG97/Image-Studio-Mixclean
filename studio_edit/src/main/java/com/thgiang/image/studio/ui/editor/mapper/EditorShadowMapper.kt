package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

object EditorShadowMapper {

    fun configureDropShadowPaint(
        appearance: EditorAppearance,
        layerAlpha: Float = 1f,
        renderScale: Float = 1f,
    ): Paint =
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
                appearance.resolvedShadowBlurRadius() * renderScale.coerceAtLeast(0.01f),
                BlurMaskFilter.Blur.NORMAL,
            )
        }

    fun shadowOffsetPx(appearance: EditorAppearance, scale: Float = 1f): Pair<Float, Float> =
        shadowOffsetWorldPx(appearance, scale)

    fun shadowOffsetWorldPx(appearance: EditorAppearance, scale: Float = 1f): Pair<Float, Float> {
        val (dx, dy) = shadowOffset(appearance.shadowAngle, appearance.shadowDistance)
        return dx * scale to dy * scale
    }

    fun shadowOffsetLocalPx(
        appearance: EditorAppearance,
        scale: Float = 1f,
        rotationDeg: Float = 0f,
    ): Pair<Float, Float> {
        val (dxW, dyW) = shadowOffsetWorldPx(appearance, scale)
        if (rotationDeg == 0f) return dxW to dyW
        val rad = Math.toRadians(-rotationDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        return (dxW * cosR - dyW * sinR) to (dxW * sinR + dyW * cosR)
    }

    fun DrawScope.drawShapeDropShadow(
        shapeType: ShapeType,
        appearance: EditorAppearance,
        scale: Float,
        cornerRadiusX: Float?,
        cornerRadiusY: Float?,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
        rotationDeg: Float = 0f,
    ) {
        if (appearance.shadowIntensity <= 0.05f) return
        val paint = configureDropShadowPaint(appearance, renderScale = scale)
        val (dx, dy) = shadowOffsetLocalPx(appearance, scale, rotationDeg)

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

    fun computeShadowBleedPx(
        appearance: EditorAppearance,
        scale: Float,
        rotationDeg: Float = 0f,
        extraStrokePx: Float = 0f,
    ): Float {
        if (appearance.shadowIntensity <= 0.05f) return extraStrokePx
        val blur = appearance.resolvedShadowBlurRadius() * scale
        val (dx, dy) = shadowOffsetWorldPx(appearance, scale)
        // Bounding box of shadow offset + blur kernel (~3σ)
        val kernel = blur * 3f
        val extentX = kotlin.math.abs(dx) + kernel
        val extentY = kotlin.math.abs(dy) + kernel
        // Rotate bbox if conservative padding is needed
        val rot = Math.toRadians(rotationDeg.toDouble())
        val pad = kotlin.math.max(
            extentX * kotlin.math.abs(kotlin.math.cos(rot)) + extentY * kotlin.math.abs(kotlin.math.sin(rot)),
            extentX * kotlin.math.abs(kotlin.math.sin(rot)) + extentY * kotlin.math.abs(kotlin.math.cos(rot)),
        )
        return pad.toFloat() + extraStrokePx + 2f // safety px
    }
}
