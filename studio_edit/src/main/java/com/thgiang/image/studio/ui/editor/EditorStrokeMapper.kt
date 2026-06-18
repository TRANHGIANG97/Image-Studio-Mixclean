package com.thgiang.image.studio.ui.editor

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.thgiang.image.core.domain.model.template.ColorArgbParser

val EditorLayer.hasStroke: Boolean
    get() = resolveStrokeWidthPx() > 0f && resolveStrokeColorArgb() != null

fun EditorLayer.resolveStrokeColorArgb(): Int? {
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

fun buildTeardropExportPath(left: Float, top: Float, w: Float, h: Float): Path {
    val r = minOf(w, h) * 0.38f
    val ptr = h * 0.22f

    return Path().apply {
        moveTo(left + r, top)
        lineTo(left + w - r, top)
        cubicTo(left + w, top, left + w, top + r, left + w, top + r)
        lineTo(left + w, top + h - r - ptr)
        cubicTo(left + w, top + h - ptr, left + w - r, top + h - ptr, left + w - r, top + h - ptr)
        lineTo(left + r * 0.5f, top + h - ptr)
        cubicTo(left, top + h - ptr, left, top + h, left, top + h)
        lineTo(left, top + h - ptr - r)
        cubicTo(left, top + r, left + r, top, left + r, top)
        close()
    }
}

fun buildStarExportPath(left: Float, top: Float, w: Float, h: Float): Path {
    val centerX = left + w / 2f
    val centerY = top + h / 2f
    val outerRadius = minOf(w, h) / 2f
    val innerRadius = outerRadius * 0.4f

    return Path().apply {
        var angle = -Math.PI / 2.0
        val angleStep = Math.PI / 5.0

        moveTo(
            centerX + (outerRadius * Math.cos(angle)).toFloat(),
            centerY + (outerRadius * Math.sin(angle)).toFloat(),
        )
        for (i in 1..10) {
            angle += angleStep
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            lineTo(
                centerX + (radius * Math.cos(angle)).toFloat(),
                centerY + (radius * Math.sin(angle)).toFloat(),
            )
        }
        close()
    }
}

fun buildHexagonExportPath(left: Float, top: Float, w: Float, h: Float): Path {
    val r = minOf(w, h) / 2f
    val centerX = left + w / 2f
    val centerY = top + h / 2f

    return Path().apply {
        for (i in 0..5) {
            val angle = Math.PI / 3.0 * i
            val px = centerX + (r * Math.cos(angle)).toFloat()
            val py = centerY + (r * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}
