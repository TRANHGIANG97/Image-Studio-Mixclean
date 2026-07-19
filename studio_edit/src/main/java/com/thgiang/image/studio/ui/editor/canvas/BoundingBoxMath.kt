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
    rotateRadiusPx: Float,
    rotateHandleOffset: Float,
    lockAspectRatio: Boolean,
    edgeHandleWidthPx: Float = 0f,
    edgeHandleHeightPx: Float = 0f,
    handleTouchPaddingPx: Float = 0f,
    isInlineEditing: Boolean = false,
    minimalTextHandles: Boolean = false,
): HandleZone {
    val local = inverseRotatePoint(touch, center, rotation)

    val hw = screenW / 2f
    val hh = screenH / 2f

    if (isInlineEditing) {
        // Keep move-handle geometry in dp-equivalent space (HandleRadiusDp is the visual reference).
        val density = handleRadius / EditorDims.HandleRadiusDp.value
        val moveGapPx = EditorDims.TextInlineMoveHandleGapDp.value * density
        val movePos = Offset(center.x, center.y - hh - moveGapPx)
        val moveHitRadius = EditorDims.TextInlineMoveHandleHitRadiusDp.value * density
        if (distance(local, movePos) <= moveHitRadius) {
            return HandleZone.Body
        }
        return HandleZone.None
    }
    val cornerHitRadius = handleRadius + handleTouchPaddingPx

    // 1. Check rotate handle first — visual radius + modest padding (same pattern as corners/edges)
    val rotPos = Offset(center.x, center.y + hh + rotateOffset + rotateHandleOffset)
    val rotateHitRadius = rotateRadiusPx + handleTouchPaddingPx
    if (distance(local, rotPos) <= rotateHitRadius) {
        return HandleZone.Rotate
    }

    // Thin text labels: prioritize body drag over edge/corner handles that dominate the box.
    if (minimalTextHandles && hh <= edgeHandleHeightPx + handleTouchPaddingPx) {
        val bodyInset = cornerHitRadius * 0.25f
        val insideBody = local.x in (center.x - hw + bodyInset)..(center.x + hw - bodyInset) &&
            local.y in (center.y - hh + bodyInset)..(center.y + hh - bodyInset)
        if (insideBody) {
            return HandleZone.Body
        }
    }

    // 2. Find the closest handle among corners and edges
    var bestZone: HandleZone = HandleZone.None
    var minDistance = Float.MAX_VALUE

    val corners = if (minimalTextHandles) {
        listOf(Offset(center.x - hw, center.y - hh) to HandleZone.Corner.TL)
    } else {
        listOf(
            Offset(center.x - hw, center.y - hh) to HandleZone.Corner.TL,
            Offset(center.x + hw, center.y - hh) to HandleZone.Corner.TR,
            Offset(center.x - hw, center.y + hh) to HandleZone.Corner.BL,
            Offset(center.x + hw, center.y + hh) to HandleZone.Corner.BR
        )
    }

    corners.forEach { (pos, zone) ->
        val dist = distance(local, pos)
        if (dist <= cornerHitRadius && dist < minDistance) {
            minDistance = dist
            bestZone = zone
        }
    }

    if (!lockAspectRatio || minimalTextHandles) {
        val edgeHandles = if (minimalTextHandles) {
            listOf(Offset(center.x + hw, center.y) to HandleZone.Edge.Right)
        } else {
            listOf(
                Offset(center.x - hw, center.y) to HandleZone.Edge.Left,
                Offset(center.x + hw, center.y) to HandleZone.Edge.Right,
                Offset(center.x, center.y - hh) to HandleZone.Edge.Top,
                Offset(center.x, center.y + hh) to HandleZone.Edge.Bottom
            )
        }

        edgeHandles.forEach { (pos, zone) ->
            if (!hitEdgeHandle(local, pos, zone, edgeHandleWidthPx, edgeHandleHeightPx, handleTouchPaddingPx, hw, hh)) {
                return@forEach
            }
            val dist = distance(local, pos)
            if (dist < minDistance) {
                minDistance = dist
                bestZone = zone
            }
        }
    }

    if (bestZone != HandleZone.None) {
        return bestZone
    }

    // 3. Check body — inset so edge/corner hit zones are not stolen by body.
    // Also, distinguish between border drag zone (Body) and tap-only inner area (BodyInner).
    val padding = cornerHitRadius * 0.35f
    val isInsideOuter = local.x in (center.x - hw + padding)..(center.x + hw - padding) &&
                        local.y in (center.y - hh + padding)..(center.y + hh - padding)
                        
    if (isInsideOuter) {
        val borderDragWidthPx = cornerHitRadius * 1.2f // about 20-24 dp
        val hasInnerArea = (hw > borderDragWidthPx) && (hh > borderDragWidthPx)
        if (hasInnerArea) {
            val isInsideInner = local.x in (center.x - hw + borderDragWidthPx)..(center.x + hw - borderDragWidthPx) &&
                                local.y in (center.y - hh + borderDragWidthPx)..(center.y + hh - borderDragWidthPx)
            if (isInsideInner) {
                return HandleZone.BodyInner
            }
        }
        return HandleZone.Body
    }
    
    return HandleZone.None
}

private fun hitEdgeHandle(
    local: Offset,
    pos: Offset,
    zone: HandleZone.Edge,
    edgeHandleWidthPx: Float,
    edgeHandleHeightPx: Float,
    touchPaddingPx: Float,
    boxHalfW: Float,
    boxHalfH: Float,
): Boolean {
    val isHorizontal = zone == HandleZone.Edge.Top || zone == HandleZone.Edge.Bottom
    val halfAlongEdge = (edgeHandleHeightPx / 2f + touchPaddingPx)
        .coerceAtMost(if (isHorizontal) boxHalfW else boxHalfH)
    val halfPerpendicular = (edgeHandleWidthPx / 2f + touchPaddingPx)
        .coerceAtMost(if (isHorizontal) boxHalfH else boxHalfW)
    val halfW = if (isHorizontal) halfAlongEdge else halfPerpendicular
    val halfH = if (isHorizontal) halfPerpendicular else halfAlongEdge
    return local.x in (pos.x - halfW)..(pos.x + halfW) &&
        local.y in (pos.y - halfH)..(pos.y + halfH)
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
    templateSize: IntSize,
): Pair<Offset, List<SnapLine>> {
    val cw = contentSize.width.toFloat()
    val ch = contentSize.height.toFloat()
    val tw = templateSize.width.toFloat()
    val th = templateSize.height.toFloat()
    val threshold = EditorConfig.SNAP_DISTANCE_PX

    val centerX = offset.x
    val centerY = offset.y
    val left = offset.x - cw / 2f
    val top = offset.y - ch / 2f
    val right = offset.x + cw / 2f
    val bottom = offset.y + ch / 2f

    val xCandidates = mutableListOf<Pair<Float, SnapLine>>()
    val yCandidates = mutableListOf<Pair<Float, SnapLine>>()

    val targetCenterX = tw / 2f
    xCandidates += targetCenterX to SnapLine(
        Offset(targetCenterX, 0f), Offset(targetCenterX, th), SnapType.CENTER_X,
    )

    val targetCenterY = th / 2f
    yCandidates += targetCenterY to SnapLine(
        Offset(0f, targetCenterY), Offset(tw, targetCenterY), SnapType.CENTER_Y,
    )

    val edgeThreshold = threshold * EditorConfig.SNAP_EDGE_FACTOR
    xCandidates += (cw / 2f) to SnapLine(Offset(0f, 0f), Offset(0f, th), SnapType.VERTICAL)
    yCandidates += (ch / 2f) to SnapLine(Offset(0f, 0f), Offset(tw, 0f), SnapType.HORIZONTAL)
    xCandidates += (tw - cw / 2f) to SnapLine(Offset(tw, 0f), Offset(tw, th), SnapType.VERTICAL)
    yCandidates += (th - ch / 2f) to SnapLine(Offset(0f, th), Offset(tw, th), SnapType.HORIZONTAL)

    val t1x = tw / 3f
    val t2x = 2f * tw / 3f
    val t1y = th / 3f
    val t2y = 2f * th / 3f
    xCandidates += t1x to SnapLine(Offset(t1x, 0f), Offset(t1x, th), SnapType.RULE_OF_THIRD)
    xCandidates += t2x to SnapLine(Offset(t2x, 0f), Offset(t2x, th), SnapType.RULE_OF_THIRD)
    yCandidates += t1y to SnapLine(Offset(0f, t1y), Offset(tw, t1y), SnapType.RULE_OF_THIRD)
    yCandidates += t2y to SnapLine(Offset(0f, t2y), Offset(tw, t2y), SnapType.RULE_OF_THIRD)

    val lines = mutableListOf<SnapLine>()

    data class AxisCandidate(val target: Float, val line: SnapLine, val distance: Float)

    val bestX = xCandidates
        .map { (target, line) ->
            val dist = when (target) {
                cw / 2f -> abs(left)
                tw - cw / 2f -> abs(right - tw)
                else -> abs(centerX - target)
            }
            val limit = if (target == cw / 2f || target == tw - cw / 2f) edgeThreshold else threshold
            AxisCandidate(target, line, dist) to limit
        }
        .filter { (candidate, limit) -> candidate.distance < limit }
        .minByOrNull { it.first.distance }
        ?.first

    val bestY = yCandidates
        .map { (target, line) ->
            val dist = when (target) {
                ch / 2f -> abs(top)
                th - ch / 2f -> abs(bottom - th)
                else -> abs(centerY - target)
            }
            val limit = if (target == ch / 2f || target == th - ch / 2f) edgeThreshold else threshold
            AxisCandidate(target, line, dist) to limit
        }
        .filter { (candidate, limit) -> candidate.distance < limit }
        .minByOrNull { it.first.distance }
        ?.first

    val snappedX = bestX?.target ?: centerX
    val snappedY = bestY?.target ?: centerY
    bestX?.line?.let { lines += it }
    bestY?.line?.let { lines += it }

    return Offset(snappedX, snappedY) to lines
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

private data class AxisSnapCandidate(val target: Float, val line: SnapLine, val distance: Float)

private fun collectTemplateSnapCandidates(
    centerX: Float,
    centerY: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cw: Float,
    ch: Float,
    tw: Float,
    th: Float,
): Pair<List<AxisSnapCandidate>, List<AxisSnapCandidate>> {
    val threshold = EditorConfig.SNAP_DISTANCE_PX
    val edgeThreshold = threshold * EditorConfig.SNAP_EDGE_FACTOR
    val xCandidates = mutableListOf<AxisSnapCandidate>()
    val yCandidates = mutableListOf<AxisSnapCandidate>()

    fun addX(target: Float, line: SnapLine, dist: Float, limit: Float = threshold) {
        if (dist < limit) xCandidates += AxisSnapCandidate(target, line, dist)
    }
    fun addY(target: Float, line: SnapLine, dist: Float, limit: Float = threshold) {
        if (dist < limit) yCandidates += AxisSnapCandidate(target, line, dist)
    }

    addX(tw / 2f, SnapLine(Offset(tw / 2f, 0f), Offset(tw / 2f, th), SnapType.CENTER_X), abs(centerX - tw / 2f))
    addY(th / 2f, SnapLine(Offset(0f, th / 2f), Offset(tw, th / 2f), SnapType.CENTER_Y), abs(centerY - th / 2f))

    addX(cw / 2f, SnapLine(Offset(0f, 0f), Offset(0f, th), SnapType.VERTICAL), abs(left), edgeThreshold)
    addY(ch / 2f, SnapLine(Offset(0f, 0f), Offset(tw, 0f), SnapType.HORIZONTAL), abs(top), edgeThreshold)
    addX(tw - cw / 2f, SnapLine(Offset(tw, 0f), Offset(tw, th), SnapType.VERTICAL), abs(right - tw))
    addY(th - ch / 2f, SnapLine(Offset(0f, th), Offset(tw, th), SnapType.HORIZONTAL), abs(bottom - th))

    val t1x = tw / 3f
    val t2x = 2f * tw / 3f
    val t1y = th / 3f
    val t2y = 2f * th / 3f
    addX(t1x, SnapLine(Offset(t1x, 0f), Offset(t1x, th), SnapType.RULE_OF_THIRD), abs(centerX - t1x))
    addX(t2x, SnapLine(Offset(t2x, 0f), Offset(t2x, th), SnapType.RULE_OF_THIRD), abs(centerX - t2x))
    addY(t1y, SnapLine(Offset(0f, t1y), Offset(tw, t1y), SnapType.RULE_OF_THIRD), abs(centerY - t1y))
    addY(t2y, SnapLine(Offset(0f, t2y), Offset(tw, t2y), SnapType.RULE_OF_THIRD), abs(centerY - t2y))

    return xCandidates to yCandidates
}

private fun resolveAxisSnap(
    current: Float,
    dragDelta: Float,
    candidates: List<AxisSnapCandidate>,
    lockedTarget: Float?,
): Pair<Float?, SnapLine?> {
    val velocityBreak = EditorConfig.SNAP_VELOCITY_BREAK_PX
    val releaseThreshold = EditorConfig.SNAP_RELEASE_PX

    if (abs(dragDelta) > velocityBreak) {
        return null to null
    }

    if (lockedTarget != null) {
        val distFromLock = current - lockedTarget
        val draggingAway = distFromLock * dragDelta > 0f && abs(dragDelta) > 0.01f
        if (draggingAway && abs(distFromLock) > EditorConfig.SNAP_DISTANCE_PX) {
            return null to null
        }
        if (abs(distFromLock) < releaseThreshold) {
            val line = candidates.firstOrNull { abs(it.target - lockedTarget) < 0.5f }?.line
            return lockedTarget to line
        }
        return null to null
    }

    val best = candidates.minByOrNull { it.distance } ?: return null to null
    return best.target to best.line
}

fun calculateSnapV2(
    offset: Offset,
    contentSize: IntSize,
    templateSize: IntSize,
    otherLayers: List<EditorLayer>,
    dragDelta: Offset = Offset.Zero,
    lockedSnapX: Float? = null,
    lockedSnapY: Float? = null,
): SnapResult {
    val cw = contentSize.width.toFloat()
    val ch = contentSize.height.toFloat()
    val threshold = EditorConfig.SNAP_DISTANCE_PX

    val centerX = offset.x
    val centerY = offset.y
    val left = centerX - cw / 2f
    val top = centerY - ch / 2f
    val right = centerX + cw / 2f
    val bottom = centerY + ch / 2f
    val tw = templateSize.width.toFloat()
    val th = templateSize.height.toFloat()

    val (xCandidates, yCandidates) = collectTemplateSnapCandidates(
        centerX, centerY, left, top, right, bottom, cw, ch, tw, th,
    ).let { it.first.toMutableList() to it.second.toMutableList() }

    for (other in otherLayers) {
        if (!other.isVisible) continue
        val otherW = other.shapeWidthPx * other.viewport.scale
        val otherH = other.shapeHeightPx * other.viewport.scale
        val otherLeft = other.viewport.offset.x - otherW / 2f
        val otherRight = other.viewport.offset.x + otherW / 2f
        val otherCenterX = other.viewport.offset.x
        val otherTop = other.viewport.offset.y - otherH / 2f
        val otherBottom = other.viewport.offset.y + otherH / 2f
        val otherCenterY = other.viewport.offset.y
        val minY = minOf(top, otherTop)
        val maxY = maxOf(bottom, otherBottom)
        val minX = minOf(left, otherLeft)
        val maxX = maxOf(right, otherRight)

        fun addX(target: Float, line: SnapLine, dist: Float) {
            if (dist < threshold) xCandidates += AxisSnapCandidate(target, line, dist)
        }
        fun addY(target: Float, line: SnapLine, dist: Float) {
            if (dist < threshold) yCandidates += AxisSnapCandidate(target, line, dist)
        }

        addX(otherLeft + cw / 2f, SnapLine(Offset(otherLeft, minY), Offset(otherLeft, maxY), SnapType.VERTICAL), abs(left - otherLeft))
        addX(otherRight - cw / 2f, SnapLine(Offset(otherRight, minY), Offset(otherRight, maxY), SnapType.VERTICAL), abs(right - otherRight))
        addX(otherCenterX, SnapLine(Offset(otherCenterX, minY), Offset(otherCenterX, maxY), SnapType.CENTER_X), abs(centerX - otherCenterX))
        addX(otherRight + cw / 2f, SnapLine(Offset(otherRight, minY), Offset(otherRight, maxY), SnapType.VERTICAL), abs(left - otherRight))
        addX(otherLeft - cw / 2f, SnapLine(Offset(otherLeft, minY), Offset(otherLeft, maxY), SnapType.VERTICAL), abs(right - otherLeft))

        addY(otherTop + ch / 2f, SnapLine(Offset(minX, otherTop), Offset(maxX, otherTop), SnapType.HORIZONTAL), abs(top - otherTop))
        addY(otherBottom - ch / 2f, SnapLine(Offset(minX, otherBottom), Offset(maxX, otherBottom), SnapType.HORIZONTAL), abs(bottom - otherBottom))
        addY(otherCenterY, SnapLine(Offset(minX, otherCenterY), Offset(maxX, otherCenterY), SnapType.CENTER_Y), abs(centerY - otherCenterY))
        addY(otherBottom + ch / 2f, SnapLine(Offset(minX, otherBottom), Offset(maxX, otherBottom), SnapType.HORIZONTAL), abs(top - otherBottom))
        addY(otherTop - ch / 2f, SnapLine(Offset(minX, otherTop), Offset(maxX, otherTop), SnapType.HORIZONTAL), abs(bottom - otherTop))
    }

    val (resolvedX, lineX) = resolveAxisSnap(centerX, dragDelta.x, xCandidates, lockedSnapX)
    val (resolvedY, lineY) = resolveAxisSnap(centerY, dragDelta.y, yCandidates, lockedSnapY)

    val snapped = Offset(resolvedX ?: centerX, resolvedY ?: centerY)
    val lines = buildList {
        lineX?.let { add(it) }
        lineY?.let { add(it) }
    }

    return SnapResult(
        offset = snapped,
        lines = lines,
        lockedSnapX = resolvedX,
        lockedSnapY = resolvedY,
    )
}

/**
 * Visual-only snap guides: returns alignment lines near [offset] without locking/correcting position.
 * Used for 1:1 finger-follow drag so purple guides never hitch the bounding box.
 */
fun calculateSnapGuidesOnly(
    offset: Offset,
    contentSize: IntSize,
    templateSize: IntSize,
    otherLayers: List<EditorLayer>,
): List<SnapLine> {
    val cw = contentSize.width.toFloat()
    val ch = contentSize.height.toFloat()
    val threshold = EditorConfig.SNAP_DISTANCE_PX

    val centerX = offset.x
    val centerY = offset.y
    val left = centerX - cw / 2f
    val top = centerY - ch / 2f
    val right = centerX + cw / 2f
    val bottom = centerY + ch / 2f
    val tw = templateSize.width.toFloat()
    val th = templateSize.height.toFloat()

    val (xCandidates, yCandidates) = collectTemplateSnapCandidates(
        centerX, centerY, left, top, right, bottom, cw, ch, tw, th,
    ).let { it.first.toMutableList() to it.second.toMutableList() }

    for (other in otherLayers) {
        if (!other.isVisible) continue
        val otherW = other.shapeWidthPx * other.viewport.scale
        val otherH = other.shapeHeightPx * other.viewport.scale
        val otherLeft = other.viewport.offset.x - otherW / 2f
        val otherRight = other.viewport.offset.x + otherW / 2f
        val otherCenterX = other.viewport.offset.x
        val otherTop = other.viewport.offset.y - otherH / 2f
        val otherBottom = other.viewport.offset.y + otherH / 2f
        val otherCenterY = other.viewport.offset.y
        val minY = minOf(top, otherTop)
        val maxY = maxOf(bottom, otherBottom)
        val minX = minOf(left, otherLeft)
        val maxX = maxOf(right, otherRight)

        fun addX(target: Float, line: SnapLine, dist: Float) {
            if (dist < threshold) xCandidates += AxisSnapCandidate(target, line, dist)
        }
        fun addY(target: Float, line: SnapLine, dist: Float) {
            if (dist < threshold) yCandidates += AxisSnapCandidate(target, line, dist)
        }

        addX(otherLeft + cw / 2f, SnapLine(Offset(otherLeft, minY), Offset(otherLeft, maxY), SnapType.VERTICAL), abs(left - otherLeft))
        addX(otherRight - cw / 2f, SnapLine(Offset(otherRight, minY), Offset(otherRight, maxY), SnapType.VERTICAL), abs(right - otherRight))
        addX(otherCenterX, SnapLine(Offset(otherCenterX, minY), Offset(otherCenterX, maxY), SnapType.CENTER_X), abs(centerX - otherCenterX))
        addX(otherRight + cw / 2f, SnapLine(Offset(otherRight, minY), Offset(otherRight, maxY), SnapType.VERTICAL), abs(left - otherRight))
        addX(otherLeft - cw / 2f, SnapLine(Offset(otherLeft, minY), Offset(otherLeft, maxY), SnapType.VERTICAL), abs(right - otherLeft))

        addY(otherTop + ch / 2f, SnapLine(Offset(minX, otherTop), Offset(maxX, otherTop), SnapType.HORIZONTAL), abs(top - otherTop))
        addY(otherBottom - ch / 2f, SnapLine(Offset(minX, otherBottom), Offset(maxX, otherBottom), SnapType.HORIZONTAL), abs(bottom - otherBottom))
        addY(otherCenterY, SnapLine(Offset(minX, otherCenterY), Offset(maxX, otherCenterY), SnapType.CENTER_Y), abs(centerY - otherCenterY))
        addY(otherBottom + ch / 2f, SnapLine(Offset(minX, otherBottom), Offset(maxX, otherBottom), SnapType.HORIZONTAL), abs(top - otherBottom))
        addY(otherTop - ch / 2f, SnapLine(Offset(minX, otherTop), Offset(maxX, otherTop), SnapType.HORIZONTAL), abs(bottom - otherTop))
    }

    val lines = mutableListOf<SnapLine>()
    xCandidates.minByOrNull { it.distance }?.line?.let { lines.add(it) }
    yCandidates.minByOrNull { it.distance }?.line?.let { lines.add(it) }
    return lines
}

data class SnapResult(
    val offset: Offset,
    val lines: List<SnapLine>,
    val lockedSnapX: Float?,
    val lockedSnapY: Float?,
)
