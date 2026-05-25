package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.components.*
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

object EditorColors {
    // Border: blue-based so visible on both light bg and dark template images
    val BorderIdle   = Color(0xFF2563EB).copy(alpha = 0.75f)
    val BorderDrag   = Color(0xFF2563EB)
    val BorderScale  = Color(0xFF7C3AED)
    val BorderRotate = Color(0xFFEF4444)
    val BorderMulti  = Color(0xFFF59E0B)

    val HandleActive   = Color(0xFF2563EB)
    val HandleInactive = Color.White        // stays white: drawn over template image
    val HandleStroke   = Color(0xFF1F2937)

    val RotateHandle       = Color(0xFF2563EB)
    val RotateHandleActive = Color(0xFFEF4444)

    // Snap lines
    val SnapCenter = Color(0xFF2563EB)
    val SnapEdge   = Color(0xFF7C3AED)
    val SnapThird  = Color(0xFFF59E0B)

    // Grid / crosshair: dark with low alpha (invisible white → visible dark)
    val Grid      = Color(0xFF111827).copy(alpha = 0.12f)
    val Crosshair = Color(0xFF111827).copy(alpha = 0.35f)
}

object EditorDims {
    val HandleRadiusDp = 4.dp
    val TouchRadiusDp = 18.dp
    val RotateLineDp = 12.dp
    val RotateHandleOffsetDp = 5.dp
    val BorderStrokeDp = 1.2.dp
    val CornerActiveScale = 1.2f
    val RotateRadiusDp = 12.dp
    val RotateRadiusActiveDp = 14.dp
    val RotateTouchRadiusDp = 30.dp
    val CrosshairSizeDp = 5.dp

    const val CORNER_EXTRA_TOUCH = 2f
    const val CORNER_GLOW_RADIUS = 2f
    const val ROTATE_GLOW_RADIUS = 3f
    const val HAPTIC_DEBOUNCE_MS = 80L
}

object EditorConfig {
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

sealed class HandleZone {
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

enum class GestureMode { IDLE, DRAG, SCALE_CORNER, ROTATE, PINCH }

data class CachedDimensions(
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
                                val localDragDelta = screenDelta
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

