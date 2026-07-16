package com.thgiang.image.studio.ui.editor.label.geometry
import com.thgiang.image.studio.ui.editor.mapper.*
import com.thgiang.image.studio.ui.editor.label.geometry.*

import com.thgiang.image.studio.ui.editor.model.*

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.PathParser
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared shape paths for preview (Compose) and export (Android Canvas).
 * Built-in shapes use bounding box (0,0)-(w,h) in local space.
 */
object EditorShapeGeometry {

    /** Fabric.js default arrow path (see admin_web fabric-shape-utils.ts). */
    const val FABRIC_ARROW_PATH: String =
        "M -100 -20 L 20 -20 L 20 -60 L 100 0 L 20 60 L 20 20 L -100 20 Z"

    fun composePath(
        shapeType: ShapeType,
        size: Size,
        cornerRadiusX: Float? = null,
        cornerRadiusY: Float? = null,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
    ): Path = androidPath(
        shapeType = shapeType,
        left = 0f,
        top = 0f,
        shapeW = size.width,
        shapeH = size.height,
        cornerRadiusX = cornerRadiusX,
        cornerRadiusY = cornerRadiusY,
        pathData = pathData,
        polygonPoints = polygonPoints,
    ).asComposePath()

    fun androidPath(
        shapeType: ShapeType,
        left: Float,
        top: Float,
        shapeW: Float,
        shapeH: Float,
        cornerRadiusX: Float? = null,
        cornerRadiusY: Float? = null,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
    ): android.graphics.Path {
        val w = shapeW.coerceAtLeast(1f)
        val h = shapeH.coerceAtLeast(1f)
        return when (shapeType) {
            ShapeType.TEXT_ONLY -> android.graphics.Path()
            ShapeType.TRIANGLE -> android.graphics.Path().apply {
                moveTo(left + w / 2f, top)
                lineTo(left + w, top + h)
                lineTo(left, top + h)
                close()
            }
            ShapeType.DIAMOND -> android.graphics.Path().apply {
                moveTo(left + w / 2f, top)
                lineTo(left + w, top + h / 2f)
                lineTo(left + w / 2f, top + h)
                lineTo(left, top + h / 2f)
                close()
            }
            ShapeType.LINE -> android.graphics.Path().apply {
                moveTo(left, top + h / 2f)
                lineTo(left + w, top + h / 2f)
            }
            ShapeType.ARROW -> scalePathDataToBounds(
                pathData = pathData?.takeIf { it.isNotBlank() } ?: FABRIC_ARROW_PATH,
                left = left,
                top = top,
                width = w,
                height = h,
            )
            ShapeType.PATH -> {
                val data = pathData?.takeIf { it.isNotBlank() } ?: return android.graphics.Path()
                scalePathDataToBounds(data, left, top, w, h)
            }
            ShapeType.POLYGON -> buildPolygonPath(left, top, w, h, polygonPoints)
                ?: buildParallelogramAndroidPath(left, top, w, h)
            ShapeType.PARALLELOGRAM -> buildParallelogramAndroidPath(left, top, w, h)
            ShapeType.STAR -> buildStarAndroidPath(left, top, w, h)
            ShapeType.HEXAGON -> buildHexagonAndroidPath(left, top, w, h)
            ShapeType.TEARDROP -> buildTeardropAndroidPath(left, top, w, h)
            ShapeType.CIRCLE -> android.graphics.Path().apply {
                addOval(left, top, left + w, top + h, android.graphics.Path.Direction.CW)
            }
            ShapeType.PILL -> {
                val rx = resolvePillCornerRadius(h, cornerRadiusX, cornerRadiusY)
                android.graphics.Path().apply {
                    addRoundRect(
                        left,
                        top,
                        left + w,
                        top + h,
                        rx,
                        rx,
                        android.graphics.Path.Direction.CW,
                    )
                }
            }
            ShapeType.CARD -> {
                val r = cornerRadiusX ?: cornerRadiusY ?: 0f
                android.graphics.Path().apply {
                    addRoundRect(
                        left,
                        top,
                        left + w,
                        top + h,
                        r,
                        r,
                        android.graphics.Path.Direction.CW,
                    )
                }
            }
        }
    }

  /** Capsule default is half height; custom rx/ry clamp to that maximum. */
    fun resolvePillCornerRadius(
        shapeHeightPx: Float,
        cornerRadiusX: Float?,
        cornerRadiusY: Float?,
    ): Float {
        val capsuleRadius = shapeHeightPx.coerceAtLeast(1f) / 2f
        val custom = cornerRadiusX ?: cornerRadiusY ?: return capsuleRadius
        return custom.coerceIn(0f, capsuleRadius)
    }

    fun isLineShape(shapeType: ShapeType): Boolean = shapeType == ShapeType.LINE

    fun isTextOnlyShape(shapeType: ShapeType): Boolean = shapeType == ShapeType.TEXT_ONLY

    fun isFilledShape(shapeType: ShapeType, shapeColorAlpha: Int, hasGradient: Boolean): Boolean {
        if (shapeType == ShapeType.LINE) return false
        if (shapeType == ShapeType.TEXT_ONLY) {
            return shapeColorAlpha > 0 || hasGradient
        }
        return shapeColorAlpha > 0 || hasGradient ||
            shapeType == ShapeType.PATH ||
            shapeType == ShapeType.ARROW
    }

    private fun scalePathDataToBounds(
        pathData: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): android.graphics.Path {
        val raw = PathParser.createPathFromPathData(pathData) ?: return android.graphics.Path()
        val bounds = RectF()
        raw.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return raw

        val matrix = Matrix()
        matrix.setRectToRect(
            bounds,
            RectF(left, top, left + width, top + height),
            Matrix.ScaleToFit.CENTER,
        )
        return android.graphics.Path().apply { raw.transform(matrix, this) }
    }

    private fun buildPolygonPath(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        flatPoints: List<Float>,
    ): android.graphics.Path? {
        if (flatPoints.size < 6 || flatPoints.size % 2 != 0) return null

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (index in flatPoints.indices step 2) {
            val x = flatPoints[index]
            val y = flatPoints[index + 1]
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
        }
        val srcW = (maxX - minX).coerceAtLeast(1f)
        val srcH = (maxY - minY).coerceAtLeast(1f)

        return android.graphics.Path().apply {
            flatPoints.forEachIndexed { index, value ->
                if (index % 2 != 0) return@forEachIndexed
                val x = left + ((value - minX) / srcW) * width
                val y = top + ((flatPoints[index + 1] - minY) / srcH) * height
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    private fun buildTeardropAndroidPath(left: Float, top: Float, w: Float, h: Float): android.graphics.Path {
        val r = minOf(w, h) * 0.38f
        val ptr = h * 0.22f

        return android.graphics.Path().apply {
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

    private fun buildParallelogramAndroidPath(left: Float, top: Float, w: Float, h: Float): android.graphics.Path {
        val skew = w * 0.2f
        return android.graphics.Path().apply {
            moveTo(left + skew, top)
            lineTo(left + w, top)
            lineTo(left + w - skew, top + h)
            lineTo(left, top + h)
            close()
        }
    }

    private fun buildStarAndroidPath(left: Float, top: Float, w: Float, h: Float): android.graphics.Path {
        val centerX = left + w / 2f
        val centerY = top + h / 2f
        val outerRadius = min(w, h) / 2f
        val innerRadius = outerRadius * 0.4f

        return android.graphics.Path().apply {
            var angle = -Math.PI / 2.0
            val angleStep = Math.PI / 5.0
            moveTo(
                centerX + (outerRadius * cos(angle)).toFloat(),
                centerY + (outerRadius * sin(angle)).toFloat(),
            )
            for (i in 1..10) {
                angle += angleStep
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                lineTo(
                    centerX + (radius * cos(angle)).toFloat(),
                    centerY + (radius * sin(angle)).toFloat(),
                )
            }
            close()
        }
    }

    private fun buildHexagonAndroidPath(left: Float, top: Float, w: Float, h: Float): android.graphics.Path {
        val r = min(w, h) / 2f
        val centerX = left + w / 2f
        val centerY = top + h / 2f

        return android.graphics.Path().apply {
            for (i in 0..5) {
                val angle = Math.PI / 3.0 * i
                val px = centerX + (r * cos(angle)).toFloat()
                val py = centerY + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
    }
}
