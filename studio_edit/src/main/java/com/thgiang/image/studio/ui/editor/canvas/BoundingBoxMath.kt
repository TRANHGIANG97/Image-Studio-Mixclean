package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.canvas.*

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

fun detectHandleRotated(
    touch: Offset,
    center: Offset,
    screenW: Float,
    screenH: Float,
    rotation: Float,
    handleRadius: Float,
    rotateOffset: Float,
    rotateTouchRadius: Float,
    rotateHandleOffset: Float,
    lockAspectRatio: Boolean
): HandleZone {
    val local = inverseRotatePoint(touch, center, rotation)

    val hw = screenW / 2f
    val hh = screenH / 2f

    // 1. Check rotate handle first
    val rotBase = Offset(center.x, center.y + hh)
    val rotPos = Offset(center.x, center.y + hh + rotateOffset + rotateHandleOffset)
    val nearRotateButton = distance(local, rotPos) <= rotateTouchRadius
    val nearRotateStem = local.x in (center.x - rotateTouchRadius * 0.35f)..(center.x + rotateTouchRadius * 0.35f) &&
        local.y in (rotBase.y - rotateTouchRadius * 0.05f)..(rotPos.y + rotateTouchRadius * 0.55f)

    if (nearRotateButton || nearRotateStem) {
        return HandleZone.Rotate
    }

    // 2. Find the closest handle among corners and edges
    var bestZone: HandleZone = HandleZone.None
    var minDistance = Float.MAX_VALUE
    val limit = handleRadius + EditorDims.CORNER_EXTRA_TOUCH

    val corners = listOf(
        Offset(center.x - hw, center.y - hh) to HandleZone.Corner.TL,
        Offset(center.x + hw, center.y - hh) to HandleZone.Corner.TR,
        Offset(center.x - hw, center.y + hh) to HandleZone.Corner.BL,
        Offset(center.x + hw, center.y + hh) to HandleZone.Corner.BR
    )

    corners.forEach { (pos, zone) ->
        val dist = distance(local, pos)
        if (dist < limit && dist < minDistance) {
            minDistance = dist
            bestZone = zone
        }
    }

    if (!lockAspectRatio) {
        val edges = listOf(
            Offset(center.x - hw, center.y) to HandleZone.Edge.Left,
            Offset(center.x + hw, center.y) to HandleZone.Edge.Right,
            Offset(center.x, center.y - hh) to HandleZone.Edge.Top,
            Offset(center.x, center.y + hh) to HandleZone.Edge.Bottom
        )

        edges.forEach { (pos, zone) ->
            val dist = distance(local, pos)
            if (dist < limit && dist < minDistance) {
                minDistance = dist
                bestZone = zone
            }
        }
    }

    if (bestZone != HandleZone.None) {
        return bestZone
    }

    // 3. Check body
    val padding = handleRadius * 0.5f
    return if (local.x in (center.x - hw + padding)..(center.x + hw - padding) &&
        local.y in (center.y - hh + padding)..(center.y + hh - padding)
    ) {
        HandleZone.Body
    } else {
        HandleZone.None
    }
}

fun calculateRotatedScale(
    handle: HandleZone.Corner,
    center: Offset,
    startTouch: Offset,
    currentTouch: Offset,
    startScale: Float,
    rotation: Float,
    screenW: Float,
    screenH: Float
): Float {
    val localStart = inverseRotatePoint(startTouch, center, rotation)
    val localCurrent = inverseRotatePoint(currentTouch, center, rotation)

    val startDist = distance(localStart, center).coerceAtLeast(1f)
    val currentDist = distance(localCurrent, center).coerceAtLeast(1f)

    val scaleFactor = currentDist / startDist
    return (startScale * scaleFactor).coerceIn(EditorConfig.MIN_SCALE, EditorConfig.MAX_SCALE)
}

fun oppositeCorner(
    handle: HandleZone.Corner,
    center: Offset,
    screenW: Float,
    screenH: Float
): Offset {
    val hw = screenW / 2f
    val hh = screenH / 2f
    return when (handle) {
        is HandleZone.Corner.TL -> Offset(center.x + hw, center.y + hh)
        is HandleZone.Corner.TR -> Offset(center.x - hw, center.y + hh)
        is HandleZone.Corner.BL -> Offset(center.x + hw, center.y - hh)
        is HandleZone.Corner.BR -> Offset(center.x - hw, center.y - hh)
    }
}

fun calculateSnap(
    offset: Offset,
    contentSize: IntSize,
    templateSize: IntSize
): Pair<Offset, List<SnapLine>> {
    val lines = mutableListOf<SnapLine>()
    var snapped = offset

    val cw = contentSize.width.toFloat()
    val ch = contentSize.height.toFloat()
    val tw = templateSize.width.toFloat()
    val th = templateSize.height.toFloat()
    val threshold = EditorConfig.SNAP_DISTANCE_PX

    val centerX = offset.x + cw / 2f
    val centerY = offset.y + ch / 2f

    val targetCenterX = tw / 2f
    if (abs(centerX - targetCenterX) < threshold) {
        snapped = snapped.copy(x = targetCenterX - cw / 2f)
        lines.add(SnapLine(Offset(targetCenterX, 0f), Offset(targetCenterX, th), SnapType.CENTER_X))
    }

    val targetCenterY = th / 2f
    if (abs(centerY - targetCenterY) < threshold) {
        snapped = snapped.copy(y = targetCenterY - ch / 2f)
        lines.add(SnapLine(Offset(0f, targetCenterY), Offset(tw, targetCenterY), SnapType.CENTER_Y))
    }

    if (abs(offset.x) < threshold * EditorConfig.SNAP_EDGE_FACTOR) {
        snapped = snapped.copy(x = 0f)
        lines.add(SnapLine(Offset(0f, 0f), Offset(0f, th), SnapType.VERTICAL))
    }
    if (abs(offset.y) < threshold * EditorConfig.SNAP_EDGE_FACTOR) {
        snapped = snapped.copy(y = 0f)
        lines.add(SnapLine(Offset(0f, 0f), Offset(tw, 0f), SnapType.HORIZONTAL))
    }
    if (abs(offset.x + cw - tw) < threshold) {
        snapped = snapped.copy(x = tw - cw)
        lines.add(SnapLine(Offset(tw, 0f), Offset(tw, th), SnapType.VERTICAL))
    }
    if (abs(offset.y + ch - th) < threshold) {
        snapped = snapped.copy(y = th - ch)
        lines.add(SnapLine(Offset(0f, th), Offset(tw, th), SnapType.HORIZONTAL))
    }

    val t1x = tw / 3f
    val t2x = 2f * tw / 3f
    val t1y = th / 3f
    val t2y = 2f * th / 3f

    when {
        abs(centerX - t1x) < threshold -> {
            snapped = snapped.copy(x = t1x - cw / 2f)
            lines.add(SnapLine(Offset(t1x, 0f), Offset(t1x, th), SnapType.RULE_OF_THIRD))
        }
        abs(centerX - t2x) < threshold -> {
            snapped = snapped.copy(x = t2x - cw / 2f)
            lines.add(SnapLine(Offset(t2x, 0f), Offset(t2x, th), SnapType.RULE_OF_THIRD))
        }
    }

    when {
        abs(centerY - t1y) < threshold -> {
            snapped = snapped.copy(y = t1y - ch / 2f)
            lines.add(SnapLine(Offset(0f, t1y), Offset(tw, t1y), SnapType.RULE_OF_THIRD))
        }
        abs(centerY - t2y) < threshold -> {
            snapped = snapped.copy(y = t2y - ch / 2f)
            lines.add(SnapLine(Offset(0f, t2y), Offset(tw, t2y), SnapType.RULE_OF_THIRD))
        }
    }

    return snapped to lines
}

fun inverseRotatePoint(point: Offset, center: Offset, angle: Float): Offset {
    val rad = Math.toRadians((-angle).toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()

    val translated = point - center
    return Offset(
        translated.x * cos - translated.y * sin,
        translated.x * sin + translated.y * cos
    ) + center
}

fun inverseRotateVector(vector: Offset, angle: Float): Offset {
    val rad = Math.toRadians((-angle).toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()

    return Offset(
        vector.x * cos - vector.y * sin,
        vector.x * sin + vector.y * cos
    )
}

fun normalizeAngleDelta(delta: Float): Float {
    var d = delta % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

fun normalizeAngleDeltaRad(delta: Float): Float {
    return atan2(sin(delta), cos(delta))
}

fun snapAngle(angle: Float, snapPoints: List<Float>, threshold: Float): Float {
    val normalized = ((angle % 360f) + 360f) % 360f
    for (snap in snapPoints) {
        val diff = abs(normalized - snap)
        val wrapDiff = abs(diff - 360f)
        if (diff <= threshold || wrapDiff <= threshold) return snap
    }
    return normalized
}

fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

fun angleBetween(p1: Offset, p2: Offset): Float {
    return atan2(p2.y - p1.y, p2.x - p1.x)
}
