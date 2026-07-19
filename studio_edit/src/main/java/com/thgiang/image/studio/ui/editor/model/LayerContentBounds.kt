package com.thgiang.image.studio.ui.editor.model

import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Axis-aligned content bounds in editor template space (center + size).
 *
 * Editor viewport offsets are relative to the template center. Convert to bitmap
 * coordinates before using these bounds to crop a rendered template bitmap.
 * Used for multi-select union boxes and user-group composite layers.
 */
data class LayerContentBounds(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
) {
    val left: Float get() = centerX - width / 2f
    val top: Float get() = centerY - height / 2f

    fun toCanvasIntRect(maxWidth: Int, maxHeight: Int): IntSizeRect {
        val l = (left + maxWidth / 2f).roundToInt().coerceIn(0, (maxWidth - 1).coerceAtLeast(0))
        val t = (top + maxHeight / 2f).roundToInt().coerceIn(0, (maxHeight - 1).coerceAtLeast(0))
        val w = width.roundToInt().coerceIn(1, maxWidth - l)
        val h = height.roundToInt().coerceIn(1, maxHeight - t)
        return IntSizeRect(left = l, top = t, width = w, height = h)
    }
}

data class IntSizeRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

fun layerContentSize(layer: EditorLayer): Pair<Float, Float> = when {
    layer.type == LayerType.IMAGE -> {
        val tight = layer.imageLayerTightMetrics()
        if (tight != null) {
            tight.contentWidth to tight.contentHeight
        } else {
            val size = layer.cropRatio.calculateSize(layer.shapeWidthPx, layer.shapeHeightPx)
            size.width.toFloat() to size.height.toFloat()
        }
    }
    layer.type == LayerType.SHADOW_REGION ->
        layer.shapeWidthPx to layer.shapeHeightPx
    layer.isVectorContentLayer ->
        layer.shapeWidthPx.coerceAtLeast(60f) to layer.shapeHeightPx.coerceAtLeast(30f)
    else ->
        layer.shapeWidthPx to layer.shapeHeightPx
}

/** Full crop/shape box size in template space (not alpha-trimmed). */
fun layerFullContentSize(layer: EditorLayer): Pair<Float, Float> = when {
    layer.type == LayerType.IMAGE -> {
        val size = layer.cropRatio.calculateSize(layer.shapeWidthPx, layer.shapeHeightPx)
        size.width.toFloat() to size.height.toFloat()
    }
    layer.type == LayerType.SHADOW_REGION ->
        layer.shapeWidthPx to layer.shapeHeightPx
    layer.isVectorContentLayer ->
        layer.shapeWidthPx.coerceAtLeast(60f) to layer.shapeHeightPx.coerceAtLeast(30f)
    else ->
        layer.shapeWidthPx to layer.shapeHeightPx
}

private fun rotatedAxisAlignedBounds(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    rotationDeg: Float,
): LayerContentBounds {
    val normalized = ((rotationDeg % 360f) + 360f) % 360f
    if (normalized == 0f || normalized == 180f) {
        return LayerContentBounds(centerX, centerY, width, height)
    }

    val halfW = width / 2f
    val halfH = height / 2f
    val rad = Math.toRadians(normalized.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    val corners = arrayOf(
        -halfW to -halfH,
        halfW to -halfH,
        halfW to halfH,
        -halfW to halfH,
    )
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for ((dx, dy) in corners) {
        val x = centerX + dx * cosR - dy * sinR
        val y = centerY + dx * sinR + dy * cosR
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }
    return LayerContentBounds(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = (maxX - minX).coerceAtLeast(1f),
        height = (maxY - minY).coerceAtLeast(1f),
    )
}

/**
 * Axis-aligned bounds after rotating a rectangle around an arbitrary pivot.
 * Matches IMAGE export: rotation pivots at the crop-box center ([EditorViewport.offset]),
 * while the drawn content may be a smaller tight rect offset inside the crop box.
 */
internal fun rotatedRectAroundPivot(
    pivotX: Float,
    pivotY: Float,
    rectCenterX: Float,
    rectCenterY: Float,
    width: Float,
    height: Float,
    rotationDeg: Float,
): LayerContentBounds {
    val hw = width / 2f
    val hh = height / 2f
    val corners = arrayOf(
        rectCenterX - hw to rectCenterY - hh,
        rectCenterX + hw to rectCenterY - hh,
        rectCenterX + hw to rectCenterY + hh,
        rectCenterX - hw to rectCenterY + hh,
    )
    val normalized = ((rotationDeg % 360f) + 360f) % 360f
    if (normalized == 0f) {
        return LayerContentBounds(rectCenterX, rectCenterY, width, height)
    }
    val rad = Math.toRadians(normalized.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for ((x, y) in corners) {
        val dx = x - pivotX
        val dy = y - pivotY
        val rx = pivotX + dx * cosR - dy * sinR
        val ry = pivotY + dx * sinR + dy * cosR
        minX = minOf(minX, rx)
        minY = minOf(minY, ry)
        maxX = maxOf(maxX, rx)
        maxY = maxOf(maxY, ry)
    }
    return LayerContentBounds(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = (maxX - minX).coerceAtLeast(1f),
        height = (maxY - minY).coerceAtLeast(1f),
    )
}

/** Per-layer bounds for user-group crop — matches renderer pivot + tight content. */
fun layerGroupingBounds(layer: EditorLayer): LayerContentBounds? {
    val pivotX = layer.viewport.offset.x
    val pivotY = layer.viewport.offset.y
    val rotation = layer.viewport.rotation
    val scale = layer.viewport.scale

    return when {
        layer.type == LayerType.IMAGE -> {
            val tight = layer.imageLayerTightMetrics()
            if (tight != null) {
                val (cx, cy) = layer.imageSelectionCenterOffset()
                rotatedRectAroundPivot(
                    pivotX = pivotX,
                    pivotY = pivotY,
                    rectCenterX = cx,
                    rectCenterY = cy,
                    width = (tight.contentWidth * scale).coerceAtLeast(1f),
                    height = (tight.contentHeight * scale).coerceAtLeast(1f),
                    rotationDeg = rotation,
                )
            } else {
                val (w, h) = layerFullContentSize(layer)
                rotatedAxisAlignedBounds(
                    centerX = pivotX,
                    centerY = pivotY,
                    width = w * scale,
                    height = h * scale,
                    rotationDeg = rotation,
                )
            }
        }
        else -> {
            val (w, h) = layerFullContentSize(layer)
            rotatedAxisAlignedBounds(
                centerX = pivotX,
                centerY = pivotY,
                width = w * scale,
                height = h * scale,
                rotationDeg = rotation,
            )
        }
    }
}

/**
 * Union AABB for user-group rasterization — tight IMAGE metrics rotated around the
 * crop pivot; full box for layers without opaque trim.
 */
fun computeUnionLayerBoundsForGrouping(layers: List<EditorLayer>): LayerContentBounds? {
    if (layers.isEmpty()) return null
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (layer in layers) {
        val bounds = layerGroupingBounds(layer) ?: continue
        minX = minOf(minX, bounds.left)
        minY = minOf(minY, bounds.top)
        maxX = maxOf(maxX, bounds.left + bounds.width)
        maxY = maxOf(maxY, bounds.top + bounds.height)
    }

    if (!minX.isFinite()) return null
    return LayerContentBounds(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = (maxX - minX).coerceAtLeast(1f),
        height = (maxY - minY).coerceAtLeast(1f),
    )
}

/** Per-layer selection bounds — same geometry as grouping (tight + rotation pivot). */
fun layerSelectionBounds(layer: EditorLayer): LayerContentBounds? = layerGroupingBounds(layer)

/** World-space corners of a layer's visible selection rect after rotation. */
fun layerOrientedCorners(layer: EditorLayer): List<Pair<Float, Float>> {
    val pivotX = layer.viewport.offset.x
    val pivotY = layer.viewport.offset.y
    val rotation = layer.viewport.rotation
    val scale = layer.viewport.scale

    val (cx, cy) = when (layer.type) {
        LayerType.IMAGE -> layer.imageSelectionCenterOffset()
        else -> pivotX to pivotY
    }
    val (w, h) = when (layer.type) {
        LayerType.IMAGE -> {
            val tight = layer.imageLayerTightMetrics()
            if (tight != null) {
                tight.contentWidth * scale to tight.contentHeight * scale
            } else {
                val (fw, fh) = layerFullContentSize(layer)
                fw * scale to fh * scale
            }
        }
        else -> {
            val (fw, fh) = layerFullContentSize(layer)
            fw * scale to fh * scale
        }
    }

    val hw = w / 2f
    val hh = h / 2f
    val localCorners = listOf(
        cx - hw to cy - hh,
        cx + hw to cy - hh,
        cx + hw to cy + hh,
        cx - hw to cy + hh,
    )

    val normalized = ((rotation % 360f) + 360f) % 360f
    if (normalized == 0f) return localCorners

    val rad = Math.toRadians(normalized.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    return localCorners.map { (x, y) ->
        val dx = x - pivotX
        val dy = y - pivotY
        pivotX + dx * cosR - dy * sinR to pivotY + dx * sinR + dy * cosR
    }
}

/**
 * World-space corners of the entire render/crop box. Unlike [layerOrientedCorners],
 * this intentionally does not alpha-trim transparent PNG padding. Group rasterization
 * must use these corners because the renderer clips and rotates the full crop box;
 * trimming before crop can otherwise cut a rotated source along one edge.
 */
fun layerFullRenderCorners(layer: EditorLayer): List<Pair<Float, Float>> {
    val pivotX = layer.viewport.offset.x
    val pivotY = layer.viewport.offset.y
    val scale = layer.viewport.scale
    val (baseW, baseH) = layerFullContentSize(layer)
    val halfW = (baseW * scale).coerceAtLeast(1f) / 2f
    val halfH = (baseH * scale).coerceAtLeast(1f) / 2f
    val localCorners = listOf(
        pivotX - halfW to pivotY - halfH,
        pivotX + halfW to pivotY - halfH,
        pivotX + halfW to pivotY + halfH,
        pivotX - halfW to pivotY + halfH,
    )

    val normalized = normalizeRotationDeg(layer.viewport.rotation)
    if (normalized == 0f) return localCorners

    val rad = Math.toRadians(normalized.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    return localCorners.map { (x, y) ->
        val dx = x - pivotX
        val dy = y - pivotY
        pivotX + dx * cosR - dy * sinR to pivotY + dx * sinR + dy * cosR
    }
}

data class OrientedLayerContentBounds(
    val bounds: LayerContentBounds,
    val rotationDeg: Float,
)

private fun normalizeRotationDeg(rotation: Float): Float =
    ((rotation % 360f) + 360f) % 360f

private fun unionAxisAlignedBounds(bounds: List<LayerContentBounds>): LayerContentBounds {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (b in bounds) {
        minX = minOf(minX, b.left)
        minY = minOf(minY, b.top)
        maxX = maxOf(maxX, b.left + b.width)
        maxY = maxOf(maxY, b.top + b.height)
    }
    return LayerContentBounds(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = (maxX - minX).coerceAtLeast(1f),
        height = (maxY - minY).coerceAtLeast(1f),
    )
}

/**
 * Union bounds for multi-select chrome. When all layers share the same rotation,
 * returns an oriented box matching that angle instead of a screen-axis-aligned rect.
 */
fun computeOrientedUnionContentBounds(layers: List<EditorLayer>): OrientedLayerContentBounds? {
    if (layers.isEmpty()) return null

    val perLayer = layers.mapNotNull { layerSelectionBounds(it) }
    if (perLayer.size != layers.size) return null

    val rotations = layers.map { normalizeRotationDeg(it.viewport.rotation) }
    val commonRot = rotations.first()
    val sameRotation = rotations.all { kotlin.math.abs(it - commonRot) < 0.5f }

    if (!sameRotation || commonRot == 0f) {
        return OrientedLayerContentBounds(
            bounds = unionAxisAlignedBounds(perLayer),
            rotationDeg = 0f,
        )
    }

    val unrotateRad = Math.toRadians(-commonRot.toDouble())
    val cosR = cos(unrotateRad).toFloat()
    val sinR = sin(unrotateRad).toFloat()

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (layer in layers) {
        for ((x, y) in layerOrientedCorners(layer)) {
            val lx = x * cosR - y * sinR
            val ly = x * sinR + y * cosR
            minX = minOf(minX, lx)
            minY = minOf(minY, ly)
            maxX = maxOf(maxX, lx)
            maxY = maxOf(maxY, ly)
        }
    }

    if (!minX.isFinite()) return null

    val localCenterX = (minX + maxX) / 2f
    val localCenterY = (minY + maxY) / 2f
    val localW = (maxX - minX).coerceAtLeast(1f)
    val localH = (maxY - minY).coerceAtLeast(1f)

    val rotateRad = Math.toRadians(commonRot.toDouble())
    val wCos = cos(rotateRad).toFloat()
    val wSin = sin(rotateRad).toFloat()
    val worldCenterX = localCenterX * wCos - localCenterY * wSin
    val worldCenterY = localCenterX * wSin + localCenterY * wCos

    return OrientedLayerContentBounds(
        bounds = LayerContentBounds(worldCenterX, worldCenterY, localW, localH),
        rotationDeg = commonRot,
    )
}

/** Union AABB of [layers] in template space (matches multi-select chrome). */
fun computeUnionContentBounds(layers: List<EditorLayer>): LayerContentBounds? =
    computeOrientedUnionContentBounds(layers)?.bounds

/**
 * Axis-aligned envelope that contains each layer's full renderer crop box,
 * including transparent padding. Used for group rasterization so the composite
 * never clips a rotated source due to alpha-tight bounds.
 */
fun computeWorldAabbEnvelope(layers: List<EditorLayer>): LayerContentBounds? {
    if (layers.isEmpty()) return null
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (layer in layers) {
        for ((x, y) in layerFullRenderCorners(layer)) {
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }
    }

    if (!minX.isFinite()) return null
    return LayerContentBounds(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = (maxX - minX).coerceAtLeast(1f),
        height = (maxY - minY).coerceAtLeast(1f),
    )
}
