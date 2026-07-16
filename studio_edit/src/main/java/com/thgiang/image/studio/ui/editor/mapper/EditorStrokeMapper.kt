package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.hasTextOnlyBackgroundDecor
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.isLabelLayer

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import com.thgiang.image.core.domain.model.template.ColorArgbParser

val EditorLayer.hasStroke: Boolean
    get() = hasShapeBorder

val EditorLayer.hasShapeBorder: Boolean
    get() {
        if (strokeColorArgb == null || resolveStrokeWidthPx() <= 0f) return false
        if (!EditorShapeGeometry.isTextOnlyShape(shapeType)) return true
        val strokeAlpha = (strokeColorArgb!! ushr 24) and 0xFF
        return strokeAlpha > 0
    }

/** Shape drop shadow needs a visible outline or fill to render against. */
val EditorLayer.supportsShapeShadow: Boolean
    get() {
        if (EditorShapeGeometry.isTextOnlyShape(shapeType)) {
            return hasTextOnlyBackgroundDecor
        }
        if (hasShapeBorder) return true
        val fillAlpha = (shapeColorArgb ushr 24) and 0xFF
        return EditorShapeGeometry.isFilledShape(shapeType, fillAlpha, fillGradient != null)
    }

/** Shape panel: shadow tab enabled for frame layers with visible geometry. */
val EditorLayer.supportsFrameShadowUi: Boolean
    get() = isFrameLayer && supportsShapeShadow

/** Shape panel: elevation tab enabled for frame layers. */
val EditorLayer.supportsFrameElevationUi: Boolean
    get() = isFrameLayer && supportsShapeShadow

val EditorLayer.supportsShapeElevation: Boolean
    get() = supportsShapeShadow

/** Text labels can use 3-D extrusion when the layer has visible text content. */
val EditorLayer.supportsTextElevation: Boolean
    get() {
        if (!isLabelLayer) return false
        if (text.isBlank()) return false
        if (textColorGradient != null) return true
        val rgb = textColorArgb and 0x00FFFFFF
        if (rgb != 0) return true
        val alpha = (textColorArgb ushr 24) and 0xFF
        return alpha > 0
    }

fun EditorLayer.resolveTextElevationColorArgb(): Int {
    textColorGradient?.let { gradient ->
        return EditorGradientMapper.parseStopArgb(gradient, 0, textColorArgb)
    }
    val alpha = (textColorArgb ushr 24) and 0xFF
    return if (alpha == 0 && (textColorArgb and 0x00FFFFFF) != 0) {
        textColorArgb or 0xFF000000.toInt()
    } else {
        textColorArgb
    }
}

fun EditorLayer.resolveShapeBorderColorArgb(): Int? {
    if (EditorShapeGeometry.isTextOnlyShape(shapeType)) {
        if (strokeColorArgb != null && resolveStrokeWidthPx() > 0f) {
            val alpha = (strokeColorArgb!! ushr 24) and 0xFF
            if (alpha > 0) return strokeColorArgb
        }
        return null
    }
    strokeColorArgb?.let { return it }
    if (!EditorShapeGeometry.isLineShape(shapeType)) return null
    if (fillGradient != null) {
        return EditorGradientMapper.parseStopArgb(fillGradient, 0, shapeColorArgb)
    }
    val alpha = (shapeColorArgb ushr 24) and 0xFF
    return if (alpha > 0) shapeColorArgb else null
}

fun EditorLayer.resolveStrokeWidthPx(): Float {
    if (EditorShapeGeometry.isLineShape(shapeType) && strokeWidthPx <= 0f) {
        return if (resolveStrokeColorArgb() != null) 6f else 0f
    }
    return strokeWidthPx
}

/** @deprecated Use [resolveShapeBorderColorArgb] for shape outline; kept for LINE layers. */
fun EditorLayer.resolveStrokeColorArgb(): Int? = resolveShapeBorderColorArgb()

object EditorStrokeMapper {
    fun extractDashGap(strokeDashArray: List<Float>, fallbackGapPx: Float = 6f): Float =
        strokeDashArray.getOrNull(1)?.takeIf { strokeDashArray.size >= 2 } ?: fallbackGapPx

    /** Build dash intervals: on-lengths from [strokeDashArray], gaps from [strokeDashGapPx] only. */
    fun resolveDashIntervals(
        strokeDashArray: List<Float>,
        strokeDashGapPx: Float,
        renderScale: Float = 1f,
    ): FloatArray? {
        if (strokeDashArray.size < 2) return null
        val gap = strokeDashGapPx.coerceAtLeast(0f)
        val intervals = strokeDashArray.mapIndexed { index, length ->
            if (index % 2 == 1) gap else length
        }
        return dashIntervalsForRender(intervals, renderScale)
    }

    /** Scale dash intervals for viewport zoom; independent of stroke width. */
    fun dashIntervalsForRender(
        strokeDashArray: List<Float>,
        renderScale: Float = 1f,
    ): FloatArray? {
        if (strokeDashArray.size < 2) return null
        val scale = renderScale.coerceAtLeast(0.01f)
        return if (scale == 1f) {
            strokeDashArray.toFloatArray()
        } else {
            strokeDashArray.map { it * scale }.toFloatArray()
        }
    }

    fun composeDashPathEffect(
        strokeDashArray: List<Float>,
        strokeDashGapPx: Float,
        renderScale: Float = 1f,
    ): PathEffect? {
        val intervals = resolveDashIntervals(strokeDashArray, strokeDashGapPx, renderScale) ?: return null
        return PathEffect.dashPathEffect(intervals, 0f)
    }

    fun configureStrokePaint(
        paint: Paint,
        strokeColorArgb: Int,
        strokeWidthPx: Float,
        strokeDashArray: List<Float>,
        strokeDashGapPx: Float,
        alpha: Int,
        renderScale: Float = 1f,
    ) {
        paint.style = Paint.Style.STROKE
        paint.color = strokeColorArgb
        paint.strokeWidth = strokeWidthPx.coerceAtLeast(0f)
        paint.alpha = alpha
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
        paint.strokeMiter = 4f
        paint.pathEffect = resolveDashIntervals(strokeDashArray, strokeDashGapPx, renderScale)?.let { intervals ->
            DashPathEffect(intervals, 0f)
        }
    }

    fun parseStrokeColor(stroke: String?): Int? = stroke?.let(ColorArgbParser::parseOrNull)
}

fun Canvas.drawShapeGeometry(
    shapeType: ShapeType,
    left: Float,
    top: Float,
    shapeW: Float,
    shapeH: Float,
    cornerRadiusX: Float?,
    cornerRadiusY: Float?,
    paint: Paint,
    pathData: String? = null,
    polygonPoints: List<Float> = emptyList(),
) {
    if (EditorShapeGeometry.isLineShape(shapeType)) {
        val midY = top + shapeH / 2f
        drawLine(left, midY, left + shapeW, midY, paint)
        return
    }

    val path = EditorShapeGeometry.androidPath(
        shapeType = shapeType,
        left = left,
        top = top,
        shapeW = shapeW,
        shapeH = shapeH,
        cornerRadiusX = cornerRadiusX,
        cornerRadiusY = cornerRadiusY,
        pathData = pathData,
        polygonPoints = polygonPoints,
    )
    drawPath(path, paint)
}

