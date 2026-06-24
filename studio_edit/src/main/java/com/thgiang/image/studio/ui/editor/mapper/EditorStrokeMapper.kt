package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.thgiang.image.core.domain.model.template.ColorArgbParser

val EditorLayer.hasStroke: Boolean
    get() = hasShapeBorder

val EditorLayer.hasShapeBorder: Boolean
    get() = !EditorShapeGeometry.isTextOnlyShape(shapeType) &&
        strokeColorArgb != null &&
        resolveStrokeWidthPx() > 0f

fun EditorLayer.resolveShapeBorderColorArgb(): Int? {
    if (EditorShapeGeometry.isTextOnlyShape(shapeType)) return null
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
    fun configureStrokePaint(
        paint: Paint,
        strokeColorArgb: Int,
        strokeWidthPx: Float,
        strokeDashArray: List<Float>,
        alpha: Int,
    ) {
        paint.style = Paint.Style.STROKE
        paint.color = strokeColorArgb
        paint.strokeWidth = strokeWidthPx.coerceAtLeast(0f)
        paint.alpha = alpha
        paint.pathEffect = if (strokeDashArray.size >= 2) {
            DashPathEffect(strokeDashArray.toFloatArray(), 0f)
        } else {
            null
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

