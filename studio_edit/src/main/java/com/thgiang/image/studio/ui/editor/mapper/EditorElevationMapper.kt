package com.thgiang.image.studio.ui.editor.mapper

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.ShapeElevationStyle
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.depthShadowBlurPx
import kotlin.math.cos
import kotlin.math.sin

private const val MAX_DEPTH_PX = 60f

fun EditorAppearance.resolvedDepthSizePx(scale: Float = 1f): Float {
    val base = depthSizePx ?: (elevationIntensity.coerceIn(0f, 1f) * MAX_DEPTH_PX)
    return base.coerceIn(0f, MAX_DEPTH_PX) * scale
}

fun EditorAppearance.resolvedExtrusionAngleDeg(): Float {
    val angle = extrusionAngle % 360f
    return if (elevationStyle == ShapeElevationStyle.INSET) angle + 180f else angle
}

fun EditorAppearance.hasShape3DDepth(scale: Float = 1f): Boolean =
    resolvedDepthSizePx(scale) > 0.5f

fun resolveShapeDepthColor(fillColorArgb: Int, depthColorArgb: Int?): Int {
    depthColorArgb?.let { return it }
    val alpha = (fillColorArgb ushr 24) and 0xFF
    if (alpha == 0) return 0xFF6B8CAE.toInt()
    val r = ((fillColorArgb shr 16) and 0xFF)
    val g = ((fillColorArgb shr 8) and 0xFF)
    val b = (fillColorArgb and 0xFF)
    // Word extrusion sides are typically a lighter tint of the face color.
    fun boost(channel: Int): Int = (channel + (255 - channel) * 0.28f).toInt().coerceIn(0, 255)
    return (alpha shl 24) or (boost(r) shl 16) or (boost(g) shl 8) or boost(b)
}

fun resolveShapeDepthBackColor(sideColorArgb: Int): Int {
    val alpha = (sideColorArgb ushr 24) and 0xFF
    val r = ((sideColorArgb shr 16) and 0xFF) * 0.82f
    val g = ((sideColorArgb shr 8) and 0xFF) * 0.82f
    val b = (sideColorArgb and 0xFF) * 0.82f
    return (alpha shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or
        (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
}

internal object Shape3DExtrusion {

    data class ExtrusionRequest(
        val shapeType: ShapeType,
        val left: Float,
        val top: Float,
        val shapeW: Float,
        val shapeH: Float,
        val cornerRadiusX: Float?,
        val cornerRadiusY: Float?,
        val pathData: String?,
        val polygonPoints: List<Float>,
        val appearance: EditorAppearance,
        val fillColorArgb: Int,
        val scale: Float,
    )

    fun drawOnCanvas(canvas: Canvas, request: ExtrusionRequest) {
        val depth = request.appearance.resolvedDepthSizePx(request.scale)
        if (depth <= 0.5f) return
        if (EditorShapeGeometry.isTextOnlyShape(request.shapeType)) return
        if (EditorShapeGeometry.isLineShape(request.shapeType)) return

        val angleRad = Math.toRadians(request.appearance.resolvedExtrusionAngleDeg().toDouble())
        val dx = (cos(angleRad) * depth).toFloat()
        val dy = (sin(angleRad) * depth).toFloat()

        val frontPath = EditorShapeGeometry.androidPath(
            shapeType = request.shapeType,
            left = request.left,
            top = request.top,
            shapeW = request.shapeW,
            shapeH = request.shapeH,
            cornerRadiusX = request.cornerRadiusX,
            cornerRadiusY = request.cornerRadiusY,
            pathData = request.pathData,
            polygonPoints = request.polygonPoints,
        )
        if (frontPath.isEmptyPath()) return

        val sideColor = resolveShapeDepthColor(request.fillColorArgb, request.appearance.depthColorArgb)
        val backColor = resolveShapeDepthBackColor(sideColor)
        val layerAlpha = (request.appearance.alpha * 255f).toInt().coerceIn(0, 255)
        val depthBlurPx = request.appearance.depthShadowBlurPx(request.scale)

        depthBlurPx?.takeIf { it > 0.5f }?.let { blurPx ->
            drawSoftDepthShadow(
                canvas = canvas,
                frontPath = frontPath,
                dx = dx,
                dy = dy,
                color = sideColor,
                alpha = (layerAlpha * 0.55f).toInt().coerceIn(0, 255),
                blurPx = blurPx,
            )
        }

        val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = sideColor
            alpha = layerAlpha
            depthBlurPx?.takeIf { it > 0.5f }?.let { blurPx ->
                setSafeBlurMaskFilter(blurPx * 0.35f)
            }
        }
        val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backColor
            alpha = layerAlpha
            depthBlurPx?.takeIf { it > 0.5f }?.let { blurPx ->
                setSafeBlurMaskFilter(blurPx * 0.35f)
            }
        }

        val points = extractPathVertices(frontPath)
        if (points.size < 3) {
            drawOffsetSilhouette(canvas, frontPath, dx, dy, sidePaint)
            return
        }

        drawSideFaces(canvas, points, dx, dy, sidePaint)
        val backPath = Path(frontPath)
        backPath.offset(dx, dy)
        canvas.drawPath(backPath, backPaint)
    }

    fun DrawScope.drawExtrusion(request: ExtrusionRequest) {
        drawIntoCanvas { canvas ->
            drawOnCanvas(canvas.nativeCanvas, request)
        }
    }

    private fun drawSoftDepthShadow(
        canvas: Canvas,
        frontPath: Path,
        dx: Float,
        dy: Float,
        color: Int,
        alpha: Int,
        blurPx: Float,
    ) {
        val shadowPath = Path(frontPath)
        shadowPath.offset(dx, dy)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            this.alpha = alpha
            setSafeBlurMaskFilter(blurPx)
        }
        canvas.drawPath(shadowPath, shadowPaint)
    }

    private fun drawSideFaces(
        canvas: Canvas,
        points: List<Offset>,
        dx: Float,
        dy: Float,
        paint: Paint,
    ) {
        val sides = buildList {
            for (i in points.indices) {
                val j = (i + 1) % points.size
                val p0 = points[i]
                val p1 = points[j]
                val avgY = (p0.y + p1.y + p1.y + dy + p0.y + dy) / 4f
                add(SideFace(p0, p1, avgY))
            }
        }.sortedByDescending { it.avgY }

        sides.forEach { side ->
            val facePath = Path().apply {
                moveTo(side.a.x, side.a.y)
                lineTo(side.b.x, side.b.y)
                lineTo(side.b.x + dx, side.b.y + dy)
                lineTo(side.a.x + dx, side.a.y + dy)
                close()
            }
            canvas.drawPath(facePath, paint)
        }
    }

    private fun drawOffsetSilhouette(canvas: Canvas, frontPath: Path, dx: Float, dy: Float, paint: Paint) {
        val backPath = Path(frontPath)
        backPath.offset(dx, dy)
        canvas.save()
        canvas.clipPath(backPath, Region.Op.DIFFERENCE)
        canvas.drawPath(backPath, paint)
        canvas.restore()
    }

    private fun extractPathVertices(path: Path, acceptableError: Float = 0.8f): List<Offset> {
        val approx = path.approximate(acceptableError)
        if (approx.size < 3) return emptyList()

        val raw = ArrayList<Offset>(approx.size / 3)
        var i = 0
        while (i + 2 < approx.size) {
            raw.add(Offset(approx[i + 1], approx[i + 2]))
            i += 3
        }
        return dedupeVertices(raw)
    }

    private fun dedupeVertices(points: List<Offset>): List<Offset> {
        if (points.isEmpty()) return points
        val out = ArrayList<Offset>(points.size)
        val minDistSq = 2f * 2f
        points.forEach { p ->
            val last = out.lastOrNull()
            if (last == null || ((p.x - last.x) * (p.x - last.x) + (p.y - last.y) * (p.y - last.y)) > minDistSq) {
                out.add(p)
            }
        }
        if (out.size >= 2) {
            val first = out.first()
            val last = out.last()
            if ((first.x - last.x) * (first.x - last.x) + (first.y - last.y) * (first.y - last.y) < minDistSq) {
                out.removeAt(out.lastIndex)
            }
        }
        return out
    }

    private data class SideFace(val a: Offset, val b: Offset, val avgY: Float)
}

private fun Path.isEmptyPath(): Boolean {
    val bounds = RectF()
    computeBounds(bounds, true)
    return bounds.width() <= 0f && bounds.height() <= 0f
}

object EditorElevationMapper {

    private fun buildExtrusionRequest(
        shapeType: ShapeType,
        appearance: EditorAppearance,
        fillColorArgb: Int,
        scale: Float,
        left: Float = 0f,
        top: Float = 0f,
        shapeW: Float,
        shapeH: Float,
        cornerRadiusX: Float?,
        cornerRadiusY: Float?,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
    ) = Shape3DExtrusion.ExtrusionRequest(
        shapeType = shapeType,
        left = left,
        top = top,
        shapeW = shapeW,
        shapeH = shapeH,
        cornerRadiusX = cornerRadiusX,
        cornerRadiusY = cornerRadiusY,
        pathData = pathData,
        polygonPoints = polygonPoints,
        appearance = appearance,
        fillColorArgb = fillColorArgb,
        scale = scale,
    )

    fun DrawScope.drawShapeElevation(
        shapeType: ShapeType,
        appearance: EditorAppearance,
        fillColorArgb: Int,
        scale: Float,
        cornerRadiusX: Float?,
        cornerRadiusY: Float?,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
    ) {
        if (!appearance.hasShape3DDepth(scale)) return
        with(Shape3DExtrusion) {
            drawExtrusion(
                buildExtrusionRequest(
                    shapeType = shapeType,
                    appearance = appearance,
                    fillColorArgb = fillColorArgb,
                    scale = scale,
                    shapeW = size.width,
                    shapeH = size.height,
                    cornerRadiusX = cornerRadiusX,
                    cornerRadiusY = cornerRadiusY,
                    pathData = pathData,
                    polygonPoints = polygonPoints,
                ),
            )
        }
    }

    fun Canvas.drawShapeElevation(
        shapeType: ShapeType,
        appearance: EditorAppearance,
        fillColorArgb: Int,
        left: Float,
        top: Float,
        shapeW: Float,
        shapeH: Float,
        cornerRadiusX: Float?,
        cornerRadiusY: Float?,
        scale: Float,
        pathData: String? = null,
        polygonPoints: List<Float> = emptyList(),
    ) {
        if (!appearance.hasShape3DDepth(scale)) return
        Shape3DExtrusion.drawOnCanvas(
            this,
            buildExtrusionRequest(
                shapeType = shapeType,
                appearance = appearance,
                fillColorArgb = fillColorArgb,
                scale = scale,
                left = left,
                top = top,
                shapeW = shapeW,
                shapeH = shapeH,
                cornerRadiusX = cornerRadiusX,
                cornerRadiusY = cornerRadiusY,
                pathData = pathData,
                polygonPoints = polygonPoints,
            ),
        )
    }
}
