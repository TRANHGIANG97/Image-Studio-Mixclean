package com.thgiang.image.studio.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*

import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.positionChanged


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId

import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * ============================================================
 * BOUNDING BOX OVERLAY V6.1 — JITTER FIX + ROTATE HANDLE FIX
 * ============================================================
 *
 * Fix từ V6:
 * 1. ✅ Pinch jitter — thêm deadzone, smoothing, tách scale/rotation
 * 2. ✅ Rotate handle visual — arc arrow luôn hướng lên trong screen space
 * 3. ✅ Rotation handle hit test cải tiến
 * 4. ✅ Centroid smoothing — exponential moving average
 * 5. ✅ Scale threshold — chỉ update khi delta đủ lớn
 * ============================================================
 */

// ============ Public Types ============

data class GestureDelta(
    val pan: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f
) {
    val isEmpty: Boolean
        get() = pan == Offset.Zero && scale == 1f && rotation == 0f

    operator fun plus(other: GestureDelta): GestureDelta = GestureDelta(
        pan = this.pan + other.pan,
        scale = this.scale * other.scale,
        rotation = this.rotation + other.rotation
    )
}

enum class SnapType { HORIZONTAL, VERTICAL, CENTER_X, CENTER_Y, RULE_OF_THIRD }

data class SnapLine(
    val start: Offset,
    val end: Offset,
    val type: SnapType
)

// ============ Constants ============

private object EditorColors {
    val BorderIdle = Color(0xFF9E9E9E)
    val BorderDrag = Color(0xFF4A90D9)
    val BorderScale = Color(0xFF7C4DFF)
    val BorderRotate = Color(0xFFE91E63)
    val BorderMulti = Color(0xFFFF9800)

    val HandleActive = Color(0xFF7C4DFF)
    val HandleInactive = Color.White
    val HandleStroke = Color(0xFF333333)

    val RotateHandle = Color(0xFF4A90D9)
    val RotateHandleActive = Color(0xFFE91E63)

    val SnapCenter = Color(0xFF4A90D9)
    val SnapEdge = Color(0xFF7C4DFF)
    val SnapThird = Color(0xFFFF9800)

    val Grid = Color.White.copy(alpha = 0.15f)
    val Crosshair = Color.White.copy(alpha = 0.45f)
}

private object EditorDims {
    val HandleRadiusDp = 8.dp
    val TouchRadiusDp = 24.dp
    val RotateLineDp = 20.dp
    val RotateHandleOffsetDp = 9.dp
    val BorderStrokeDp = 2.dp
    val CornerActiveScale = 1.4f
    val RotateRadiusDp = 20.dp
    val RotateRadiusActiveDp = 22.dp
    val RotateTouchRadiusDp = 42.dp
    val CrosshairSizeDp = 8.dp

    const val CORNER_EXTRA_TOUCH = 4f
    const val CORNER_GLOW_RADIUS = 5f
    const val ROTATE_GLOW_RADIUS = 6f
    const val HAPTIC_DEBOUNCE_MS = 80L
}

private object EditorConfig {
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 5f
    const val SNAP_ANGLE_THRESHOLD = 6f
    const val SNAP_DISTANCE_PX = 12f
    const val SNAP_EDGE_FACTOR = 0.8f

    // V6.1: Pinch smoothing config
    const val PINCH_SCALE_THRESHOLD = 0.02f      // 2% scale change mới update
    const val PINCH_ROTATION_THRESHOLD = 1.5f    // 1.5° rotation mới update
    const val PINCH_CENTROID_SMOOTHING = 0.7f    // EMA factor (0-1, cao = mượt hơn)
    const val PINCH_DEADZONE_PX = 3f             // Bỏ qua movement nhỏ hơn 3px
}

private val SNAP_ANGLES = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)

// ============ Internal Types ============

private sealed class HandleZone {
    data object None : HandleZone()
    data object Body : HandleZone()
    data object Rotate : HandleZone()
    sealed class Corner : HandleZone() {
        data object TL : Corner()
        data object TR : Corner()
        data object BL : Corner()
        data object BR : Corner()
    }
}

private enum class GestureMode { IDLE, DRAG, SCALE_CORNER, ROTATE, PINCH }

private data class CachedDimensions(
    val handleRadiusPx: Float,
    val touchRadiusPx: Float,
    val rotateLinePx: Float,
    val rotateHandleOffsetPx: Float,
    val borderStrokePx: Float,
    val rotateRadiusPx: Float,
    val rotateRadiusActivePx: Float,
    val rotateTouchRadiusPx: Float,
    val crosshairSizePx: Float
)

// ============ Main Composable ============

@Composable
fun BoundingBoxOverlayV6(
    modifier: Modifier = Modifier,
    contentWidth: Float,
    contentHeight: Float,
    viewport: EditorViewport,
    displayScale: Float,
    templateSize: IntSize,
    lockAspectRatio: Boolean = true,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showBoundingBox: Boolean = true,
    onBoundingBoxVisible: (Boolean) -> Unit = {}
) {
    require(contentWidth > 0f) { "contentWidth must be > 0" }
    require(contentHeight > 0f) { "contentHeight must be > 0" }
    require(displayScale > 0f) { "displayScale must be > 0" }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentViewport by rememberUpdatedState(viewport)

    val dimensions = remember(density) {
        CachedDimensions(
            handleRadiusPx = with(density) { EditorDims.HandleRadiusDp.toPx() },
            touchRadiusPx = with(density) { EditorDims.TouchRadiusDp.toPx() },
            rotateLinePx = with(density) { EditorDims.RotateLineDp.toPx() },
            rotateHandleOffsetPx = with(density) { EditorDims.RotateHandleOffsetDp.toPx() },
            borderStrokePx = with(density) { EditorDims.BorderStrokeDp.toPx() },
            rotateRadiusPx = with(density) { EditorDims.RotateRadiusDp.toPx() },
            rotateRadiusActivePx = with(density) { EditorDims.RotateRadiusActiveDp.toPx() },
            rotateTouchRadiusPx = with(density) { EditorDims.RotateTouchRadiusDp.toPx() },
            crosshairSizePx = with(density) { EditorDims.CrosshairSizeDp.toPx() }
        )
    }

    var gestureMode by remember { mutableStateOf(GestureMode.IDLE) }
    var activeHandle by remember { mutableStateOf<HandleZone>(HandleZone.None) }
    var showSnap by remember { mutableStateOf(false) }
    var snapLines by remember { mutableStateOf<List<SnapLine>>(emptyList()) }

    val borderColor by animateColorAsState(
        targetValue = when (gestureMode) {
            GestureMode.DRAG -> EditorColors.BorderDrag
            GestureMode.SCALE_CORNER -> EditorColors.BorderScale
            GestureMode.ROTATE -> EditorColors.BorderRotate
            GestureMode.PINCH -> EditorColors.BorderMulti
            GestureMode.IDLE -> EditorColors.BorderIdle
        },
        animationSpec = tween(120),
        label = "borderColor"
    )

    val snapAlpha by animateFloatAsState(
        targetValue = if (showSnap) 0.85f else 0f,
        animationSpec = tween(100),
        label = "snapAlpha"
    )

    var lastHapticTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(contentWidth, contentHeight, displayScale, lockAspectRatio, showBoundingBox) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    val screenW = contentWidth * currentViewport.scale * displayScale
                    val screenH = contentHeight * currentViewport.scale * displayScale

                    activeHandle = detectHandleRotated(
                        touch = down.position,
                        center = center,
                        screenW = screenW,
                        screenH = screenH,
                        rotation = currentViewport.rotation,
                        handleRadius = dimensions.touchRadiusPx,
                        rotateOffset = dimensions.rotateLinePx,
                        rotateTouchRadius = dimensions.rotateTouchRadiusPx,
                        rotateHandleOffset = dimensions.rotateHandleOffsetPx
                    )

                    gestureMode = when (activeHandle) {
                        HandleZone.Body -> GestureMode.DRAG
                        HandleZone.Rotate -> GestureMode.ROTATE
                        is HandleZone.Corner -> GestureMode.SCALE_CORNER
                        HandleZone.None -> GestureMode.IDLE
                    }

                    if (gestureMode != GestureMode.IDLE && !showBoundingBox) {
                        onBoundingBoxVisible(true)
                    }

                    if (gestureMode == GestureMode.IDLE) return@awaitEachGesture

                    performDebouncedHaptic(haptic, lastHapticTime) { lastHapticTime = it }

                    // ── LOCAL VARIABLES ──
                    var localStartTransform = currentViewport
                    var localStartTouch = down.position
                    var localLastTouch = down.position
                    var localLastSnappedAngle = Float.NaN

                    // Pinch-specific locals
                    var localPinchId1: PointerId? = null
                    var localPinchId2: PointerId? = null
                    var localPinchStartDist = 1f
                    var localPinchStartAngle = 0f
                    var localPinchStartScale = 1f
                    var localPinchStartRotation = 0f
                    var localPinchStartCentroid = Offset.Zero

                    // V6.1: Smoothed centroid
                    var localSmoothedCentroid = Offset.Zero
                    var localLastRawCentroid = Offset.Zero

                    // Rotation-specific locals
                    var localRotateStartAngleRad = 0f
                    var localRotateStartRotation = 0f

                    // Scale-specific locals
                    var localScaleStartScale = 1f
                    var localScaleHandle: HandleZone.Corner? = null

                    when (gestureMode) {
                        GestureMode.ROTATE -> {
                            localRotateStartAngleRad = atan2(
                                down.position.y - center.y,
                                down.position.x - center.x
                            )
                            localRotateStartRotation = currentViewport.rotation
                        }
                        GestureMode.SCALE_CORNER -> {
                            localScaleStartScale = currentViewport.scale
                            localScaleHandle = activeHandle as? HandleZone.Corner
                        }
                        else -> {}
                    }

                    var hasMoved = false

                    // ── MAIN GESTURE LOOP ──
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.isEmpty()) break

                        // Detect pinch: 2+ fingers
                        if (pressed.size >= 2 && gestureMode != GestureMode.PINCH) {
                            val p1 = pressed[0]
                            val p2 = pressed[1]
                            localPinchId1 = p1.id
                            localPinchId2 = p2.id
                            localPinchStartDist = distance(p1.position, p2.position).coerceAtLeast(1f)
                            localPinchStartAngle = angleBetween(p1.position, p2.position)
                            localPinchStartScale = currentViewport.scale
                            localPinchStartRotation = currentViewport.rotation

                            val initialCentroid = (p1.position + p2.position) / 2f
                            localPinchStartCentroid = initialCentroid
                            localSmoothedCentroid = initialCentroid
                            localLastRawCentroid = initialCentroid

                            gestureMode = GestureMode.PINCH
                            performDebouncedHaptic(haptic, lastHapticTime) { lastHapticTime = it }
                            continue
                        }

                        // Drop to 1 finger from pinch
                        if (pressed.size == 1 && gestureMode == GestureMode.PINCH) {
                            val remaining = pressed.first()
                            if (hasMoved) {
                                showSnap = false
                                snapLines = emptyList()
                                onGestureEnd()
                            }
                            gestureMode = GestureMode.IDLE
                            activeHandle = HandleZone.None
                            localStartTouch = remaining.position
                            localLastTouch = remaining.position
                            localStartTransform = currentViewport
                            continue
                        }

                        when (gestureMode) {
                            GestureMode.PINCH -> {
                                val c1 = event.changes.find { it.id == localPinchId1 } ?: continue
                                val c2 = event.changes.find { it.id == localPinchId2 } ?: continue
                                if (!c1.positionChanged() && !c2.positionChanged()) continue

                                val p1 = c1.position
                                val p2 = c2.position

                                // ── V6.1: SCALE với threshold ──
                                val currentDist = distance(p1, p2).coerceAtLeast(1f)
                                val rawScaleFactor = currentDist / localPinchStartDist

                                // Chỉ update scale nếu thay đổi đủ lớn
                                val clampedScaleFactor = if (abs(rawScaleFactor - 1f) < EditorConfig.PINCH_SCALE_THRESHOLD) {
                                    1f
                                } else {
                                    rawScaleFactor
                                }

                                val newScale = if (lockAspectRatio) {
                                    (localPinchStartScale * clampedScaleFactor)
                                        .coerceIn(EditorConfig.MIN_SCALE, EditorConfig.MAX_SCALE)
                                } else {
                                    (localPinchStartScale * clampedScaleFactor)
                                        .coerceIn(EditorConfig.MIN_SCALE, EditorConfig.MAX_SCALE)
                                }

                                // ── V6.1: ROTATION với threshold ──
                                val currentAngle = angleBetween(p1, p2)
                                val rawRotationDelta = normalizeAngleDelta(
                                    Math.toDegrees((currentAngle - localPinchStartAngle).toDouble()).toFloat()
                                )

                                // Chỉ update rotation nếu thay đổi đủ lớn
                                val clampedRotationDelta = if (abs(rawRotationDelta) < EditorConfig.PINCH_ROTATION_THRESHOLD) {
                                    0f
                                } else {
                                    rawRotationDelta
                                }

                                val rawRotation = (localPinchStartRotation + clampedRotationDelta) % 360f
                                val snappedRotation = snapAngle(rawRotation, SNAP_ANGLES, EditorConfig.SNAP_ANGLE_THRESHOLD)

                                // ── V6.1: CENTROID SMOOTHING ──
                                val rawCentroid = (p1 + p2) / 2f
                                val centroidDelta = rawCentroid - localLastRawCentroid

                                // Bỏ qua movement nhỏ (deadzone)
                                val filteredDelta = if (centroidDelta.getDistance() < EditorConfig.PINCH_DEADZONE_PX) {
                                    Offset.Zero
                                } else {
                                    centroidDelta
                                }

                                // Exponential moving average
                                localSmoothedCentroid = Offset(
                                    localSmoothedCentroid.x + EditorConfig.PINCH_CENTROID_SMOOTHING * filteredDelta.x,
                                    localSmoothedCentroid.y + EditorConfig.PINCH_CENTROID_SMOOTHING * filteredDelta.y
                                )

                                val screenCentroidDelta = localSmoothedCentroid - localPinchStartCentroid
                                val templateCentroidDelta = Offset(
                                    screenCentroidDelta.x / displayScale,
                                    screenCentroidDelta.y / displayScale
                                )

                                // Haptic
                                if (snappedRotation != rawRotation &&
                                    (localLastSnappedAngle.isNaN() || abs(snappedRotation - localLastSnappedAngle) > 0.5f)
                                ) {
                                    performDebouncedHaptic(haptic, lastHapticTime, HapticFeedbackType.LongPress) {
                                        lastHapticTime = it
                                    }
                                    localLastSnappedAngle = snappedRotation
                                }

                                c1.consume()
                                c2.consume()
                                localLastRawCentroid = rawCentroid
                                hasMoved = true

                                onGesture(GestureDelta(
                                    pan = templateCentroidDelta,
                                    scale = newScale / currentViewport.scale,
                                    rotation = snappedRotation - currentViewport.rotation
                                ))
                            }

                            GestureMode.DRAG -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val screenDelta = change.position - change.previousPosition
                                val localDragDelta = inverseRotateVector(screenDelta, currentViewport.rotation)
                                val templateDelta = Offset(
                                    localDragDelta.x / displayScale,
                                    localDragDelta.y / displayScale
                                )

                                val tentativeOffset = currentViewport.offset + templateDelta
                                val scaledContentSize = IntSize(
                                    (contentWidth * currentViewport.scale).toInt(),
                                    (contentHeight * currentViewport.scale).toInt()
                                )

                                val (snappedOffset, lines) = calculateSnap(
                                    offset = tentativeOffset,
                                    contentSize = scaledContentSize,
                                    templateSize = templateSize
                                )

                                showSnap = lines.isNotEmpty()
                                snapLines = lines

                                change.consume()
                                hasMoved = true

                                onGesture(GestureDelta(
                                    pan = snappedOffset - currentViewport.offset
                                ))
                            }

                            GestureMode.ROTATE -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val currentAngleRad = atan2(
                                    change.position.y - center.y,
                                    change.position.x - center.x
                                )
                                val rawDeltaRad = normalizeAngleDeltaRad(currentAngleRad - localRotateStartAngleRad)
                                val rawDeltaDeg = Math.toDegrees(rawDeltaRad.toDouble()).toFloat()
                                val rawRotation = (localRotateStartRotation + rawDeltaDeg) % 360f
                                val snappedRotation = snapAngle(rawRotation, SNAP_ANGLES, EditorConfig.SNAP_ANGLE_THRESHOLD)

                                if (snappedRotation != rawRotation &&
                                    (localLastSnappedAngle.isNaN() || abs(snappedRotation - localLastSnappedAngle) > 0.5f)
                                ) {
                                    performDebouncedHaptic(haptic, lastHapticTime, HapticFeedbackType.LongPress) {
                                        lastHapticTime = it
                                    }
                                    localLastSnappedAngle = snappedRotation
                                }

                                change.consume()
                                hasMoved = true

                                onGesture(GestureDelta(
                                    rotation = snappedRotation - currentViewport.rotation
                                ))
                            }

                            GestureMode.SCALE_CORNER -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val handle = localScaleHandle ?: continue
                                val newScale = calculateRotatedScale(
                                    handle = handle,
                                    center = center,
                                    startTouch = localStartTouch,
                                    currentTouch = change.position,
                                    startScale = localScaleStartScale,
                                    rotation = currentViewport.rotation,
                                    screenW = screenW,
                                    screenH = screenH
                                )

                                change.consume()
                                hasMoved = true

                                onGesture(GestureDelta(
                                    scale = newScale / currentViewport.scale
                                ))
                            }

                            GestureMode.IDLE -> { }
                        }
                    }

                    if (hasMoved) {
                        showSnap = false
                        snapLines = emptyList()
                        onGestureEnd()
                    }
                    gestureMode = GestureMode.IDLE
                    activeHandle = HandleZone.None
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val cx = center.x
            val cy = center.y

            val screenW = contentWidth * viewport.scale * displayScale
            val screenH = contentHeight * viewport.scale * displayScale
            val hw = screenW / 2f
            val hh = screenH / 2f

            if (showBoundingBox && snapAlpha > 0.01f) {
                drawSnapLines(
                    lines = snapLines,
                    alpha = snapAlpha,
                    displayScale = displayScale,
                    screenOriginX = cx - viewport.offset.x * displayScale - (templateSize.width / 2f) * displayScale,
                    screenOriginY = cy - viewport.offset.y * displayScale - (templateSize.height / 2f) * displayScale,
                    templateSize = templateSize
                )
            }

            if (showBoundingBox) {
                drawRotatedOverlay(
                    cx = cx,
                    cy = cy,
                    hw = hw,
                    hh = hh,
                    screenW = screenW,
                    screenH = screenH,
                    rotation = viewport.rotation,
                    borderColor = borderColor,
                    dimensions = dimensions,
                    gestureMode = gestureMode,
                    activeHandle = activeHandle,
                    isGestureActive = gestureMode != GestureMode.IDLE
                )
            }
        }
    }
}

// ============ Debounced Haptic ============

private fun performDebouncedHaptic(
    haptic: HapticFeedback,
    lastTime: Long,
    type: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onUpdate: (Long) -> Unit
) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastTime >= EditorDims.HAPTIC_DEBOUNCE_MS) {
        haptic.performHapticFeedback(type)
        onUpdate(currentTime)
    }
}

// ============ Handle Detection ============

private fun detectHandleRotated(
    touch: Offset,
    center: Offset,
    screenW: Float,
    screenH: Float,
    rotation: Float,
    handleRadius: Float,
    rotateOffset: Float,
    rotateTouchRadius: Float,
    rotateHandleOffset: Float
): HandleZone {
    val local = inverseRotatePoint(touch, center, rotation)

    val hw = screenW / 2f
    val hh = screenH / 2f

    val corners = listOf(
        Offset(center.x - hw, center.y - hh) to HandleZone.Corner.TL,
        Offset(center.x + hw, center.y - hh) to HandleZone.Corner.TR,
        Offset(center.x - hw, center.y + hh) to HandleZone.Corner.BL,
        Offset(center.x + hw, center.y + hh) to HandleZone.Corner.BR
    )

    corners.forEach { (pos, zone) ->
        if (distance(local, pos) < handleRadius + EditorDims.CORNER_EXTRA_TOUCH) return zone
    }

    val rotBase = Offset(center.x, center.y + hh)
    val rotPos = Offset(center.x, center.y + hh + rotateOffset + rotateHandleOffset)
    val nearRotateButton = distance(local, rotPos) <= rotateTouchRadius
    val nearRotateStem = local.x in (center.x - rotateTouchRadius * 0.35f)..(center.x + rotateTouchRadius * 0.35f) &&
        local.y in (rotBase.y - rotateTouchRadius * 0.05f)..(rotPos.y + rotateTouchRadius * 0.55f)

    if (nearRotateButton || nearRotateStem) {
        return HandleZone.Rotate
    }

    val padding = handleRadius * 0.5f
    return if (local.x in (center.x - hw + padding)..(center.x + hw - padding) &&
        local.y in (center.y - hh + padding)..(center.y + hh - padding)
    ) {
        HandleZone.Body
    } else {
        HandleZone.None
    }
}

// ============ Scale Calculation ============

private fun calculateRotatedScale(
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
    val opposite = oppositeCorner(handle, center, screenW, screenH)

    val startDist = distance(localStart, opposite).coerceAtLeast(1f)
    val currentDist = distance(localCurrent, opposite).coerceAtLeast(1f)

    val scaleFactor = currentDist / startDist
    return (startScale * scaleFactor).coerceIn(EditorConfig.MIN_SCALE, EditorConfig.MAX_SCALE)
}

private fun oppositeCorner(
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

// ============ Snap Calculation ============

private fun calculateSnap(
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

// ============ Drawing Helpers ============

private fun DrawScope.drawSnapLines(
    lines: List<SnapLine>,
    alpha: Float,
    displayScale: Float,
    screenOriginX: Float,
    screenOriginY: Float,
    templateSize: IntSize
) {
    lines.forEach { line ->
        val color = when (line.type) {
            SnapType.CENTER_X, SnapType.CENTER_Y -> EditorColors.SnapCenter
            SnapType.RULE_OF_THIRD -> EditorColors.SnapThird
            else -> EditorColors.SnapEdge
        }.copy(alpha = alpha)

        val strokeWidth = when (line.type) {
            SnapType.CENTER_X, SnapType.CENTER_Y -> 2.dp.toPx()
            else -> 1.5f.dp.toPx()
        }

        val startScreen = Offset(
            screenOriginX + line.start.x * displayScale,
            screenOriginY + line.start.y * displayScale
        )
        val endScreen = Offset(
            screenOriginX + line.end.x * displayScale,
            screenOriginY + line.end.y * displayScale
        )

        drawLine(
            color = color,
            start = startScreen,
            end = endScreen,
            strokeWidth = strokeWidth,
            pathEffect = if (line.type == SnapType.RULE_OF_THIRD) {
                PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
            } else null
        )
    }
}

private fun DrawScope.drawRotatedOverlay(
    cx: Float,
    cy: Float,
    hw: Float,
    hh: Float,
    screenW: Float,
    screenH: Float,
    rotation: Float,
    borderColor: Color,
    dimensions: CachedDimensions,
    gestureMode: GestureMode,
    activeHandle: HandleZone,
    isGestureActive: Boolean
) {
    withTransform({
        rotate(degrees = rotation, pivot = Offset(cx, cy))
    }) {
        val pathEffect = when (gestureMode) {
            GestureMode.DRAG -> PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
            GestureMode.SCALE_CORNER, GestureMode.PINCH -> PathEffect.dashPathEffect(floatArrayOf(8f, 4f, 2f, 4f), 0f)
            else -> null
        }

        drawRect(
            color = borderColor,
            topLeft = Offset(cx - hw, cy - hh),
            size = Size(screenW, screenH),
            style = Stroke(width = dimensions.borderStrokePx, pathEffect = pathEffect)
        )

        // Corner handles
        val corners = listOf(
            Offset(cx - hw, cy - hh) to HandleZone.Corner.TL,
            Offset(cx + hw, cy - hh) to HandleZone.Corner.TR,
            Offset(cx - hw, cy + hh) to HandleZone.Corner.BL,
            Offset(cx + hw, cy + hh) to HandleZone.Corner.BR
        )

        corners.forEach { (pos, zone) ->
            val isActive = gestureMode == GestureMode.SCALE_CORNER && activeHandle == zone
            val radius = if (isActive) dimensions.handleRadiusPx * EditorDims.CornerActiveScale else dimensions.handleRadiusPx
            val color = if (isActive) EditorColors.HandleActive else EditorColors.HandleInactive

            if (isActive) {
                drawCircle(
                    color = EditorColors.HandleActive.copy(alpha = 0.28f),
                    radius = radius + EditorDims.CORNER_GLOW_RADIUS,
                    center = pos
                )
            }
            drawCircle(color, radius, pos)
            drawCircle(EditorColors.HandleStroke, radius, pos, style = Stroke(dimensions.borderStrokePx))
        }

        // Rotation handle
        val rotBase = Offset(cx, cy + hh)
        val rotPos = Offset(cx, cy + hh + dimensions.rotateLinePx + dimensions.rotateHandleOffsetPx)

        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = rotBase,
            end = rotPos,
            strokeWidth = 1.5f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )

        val isRotating = gestureMode == GestureMode.ROTATE
        val rotColor = if (isRotating) EditorColors.RotateHandleActive else EditorColors.RotateHandle
        val rotR = if (isRotating) dimensions.rotateRadiusActivePx else dimensions.rotateRadiusPx

        drawCircle(
            color = rotColor.copy(alpha = if (isRotating) 0.28f else 0.16f),
            radius = rotR + EditorDims.ROTATE_GLOW_RADIUS,
            center = rotPos
        )
        drawCircle(Color.White, rotR, rotPos)
        drawCircle(rotColor, rotR, rotPos, style = Stroke(if (isRotating) 4.dp.toPx() else 3.dp.toPx()))

        // V6.1: Rotate handle icon — arc arrow LUÔN hướng lên trong screen space
        // Vì đang trong withTransform { rotate(...) }, ta vẽ ngược lại bằng -rotation
        // để icon luôn "đứng yên" trong screen space
        drawRotatingHandleIcon(
            center = rotPos,
            radius = rotR,
            rotation = rotation,
            color = rotColor
        )

        // Center crosshair
        val cc = EditorColors.Crosshair
        drawLine(cc, Offset(cx - dimensions.crosshairSizePx, cy), Offset(cx + dimensions.crosshairSizePx, cy), 1.5f.dp.toPx())
        drawLine(cc, Offset(cx, cy - dimensions.crosshairSizePx), Offset(cx, cy + dimensions.crosshairSizePx), 1.5f.dp.toPx())

        // Rule of thirds grid
        if (isGestureActive) {
            drawRuleOfThirdsGrid(cx, cy, hw, hh)
        }
    }
}

/**
 * V6.1: Vẽ icon xoay (arc + arrow) LUÔN hướng lên trong screen space.
 * Dùng withTransform inverse rotation để icon không bị xoay theo box.
 */
private fun DrawScope.drawRotatingHandleIcon(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color
) {
    withTransform({
        // Inverse rotation để icon luôn "đứng yên" trong screen space
        rotate(degrees = -rotation, pivot = center)
    }) {
        val iconRadius = radius * 0.7f
        val strokeWidth = 2.dp.toPx()

        // Vẽ arc 270° (3/4 vòng tròn)
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - iconRadius, center.y - iconRadius),
            size = Size(iconRadius * 2, iconRadius * 2),
            style = Stroke(strokeWidth)
        )

        // Vẽ mũi tên hướng lên trên (screen space)
        val arrowSize = iconRadius * 0.5f
        val arrowTop = Offset(center.x, center.y - iconRadius - arrowSize * 0.3f)
        val arrowLeft = Offset(center.x - arrowSize * 0.4f, center.y - iconRadius + arrowSize * 0.3f)
        val arrowRight = Offset(center.x + arrowSize * 0.4f, center.y - iconRadius + arrowSize * 0.3f)

        drawLine(color, arrowLeft, arrowTop, strokeWidth)
        drawLine(color, arrowRight, arrowTop, strokeWidth)
    }
}

private fun DrawScope.drawRuleOfThirdsGrid(cx: Float, cy: Float, hw: Float, hh: Float) {
    val color = EditorColors.Grid
    val strokeWidth = 0.5f.dp.toPx()

    for (i in 1..2) {
        val x = cx - hw + (hw * 2f / 3f) * i
        drawLine(color, Offset(x, cy - hh), Offset(x, cy + hh), strokeWidth)
    }
    for (i in 1..2) {
        val y = cy - hh + (hh * 2f / 3f) * i
        drawLine(color, Offset(cx - hw, y), Offset(cx + hw, y), strokeWidth)
    }
}

// ============ Math Helpers ============

private fun inverseRotatePoint(point: Offset, center: Offset, angle: Float): Offset {
    val rad = Math.toRadians((-angle).toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()

    val translated = point - center
    return Offset(
        translated.x * cos - translated.y * sin,
        translated.x * sin + translated.y * cos
    ) + center
}

private fun inverseRotateVector(vector: Offset, angle: Float): Offset {
    val rad = Math.toRadians((-angle).toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()

    return Offset(
        vector.x * cos - vector.y * sin,
        vector.x * sin + vector.y * cos
    )
}

private fun normalizeAngleDelta(delta: Float): Float {
    var d = delta % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun normalizeAngleDeltaRad(delta: Float): Float {
    return atan2(sin(delta), cos(delta))
}

private fun snapAngle(angle: Float, snapPoints: List<Float>, threshold: Float): Float {
    val normalized = ((angle % 360f) + 360f) % 360f
    for (snap in snapPoints) {
        val diff = abs(normalized - snap)
        val wrapDiff = abs(diff - 360f)
        if (diff <= threshold || wrapDiff <= threshold) return snap
    }
    return normalized
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

private fun angleBetween(p1: Offset, p2: Offset): Float {
    return atan2(p2.y - p1.y, p2.x - p1.x)
}
