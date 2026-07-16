package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.canvas.*

import com.thgiang.image.studio.ui.editor.model.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
    val rotation: Float = 0f,
    val deltaWidth: Float = 0f,
    val deltaHeight: Float = 0f
) {
    val isEmpty: Boolean
        get() = pan == Offset.Zero && scale == 1f && rotation == 0f && deltaWidth == 0f && deltaHeight == 0f

    operator fun plus(other: GestureDelta): GestureDelta = GestureDelta(
        pan = this.pan + other.pan,
        scale = this.scale * other.scale,
        rotation = this.rotation + other.rotation,
        deltaWidth = this.deltaWidth + other.deltaWidth,
        deltaHeight = this.deltaHeight + other.deltaHeight
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
    /** Visual radius of corner scale handles. */
    val HandleVisualRadiusDp = 8.dp
    /** Hit-test base radius for corner handles (unchanged when visual shrinks). */
    val HandleRadiusDp = 12.dp
    val TouchRadiusDp = 32.dp
    /** Extra hit slop around handle visuals — forgiving taps a few px off-center. */
    val HandleTouchPaddingDp = 8.dp
    val RotateLineDp = 28.dp
    val RotateHandleOffsetDp = 5.dp
    val BorderStrokeDp = 1.2.dp
    val CornerActiveScale = 1.2f
    val RotateRadiusDp = 12.dp
    val RotateRadiusActiveDp = 14.dp
    val CrosshairSizeDp = 5.dp

    /** Inline text edit: move handle sits above the bbox top edge (clears sample replace button). */
    val TextInlineMoveHandleGapDp = 52.dp
    val TextInlineMoveHandleRadiusDp = 14.dp
    val TextInlineMoveHandleHitRadiusDp = 18.dp

    const val CORNER_GLOW_RADIUS = 2f
    const val ROTATE_GLOW_RADIUS = 3f
    const val HAPTIC_DEBOUNCE_MS = 80L
}

object EditorConfig {
    const val MIN_SCALE = 0.05f
    const val MAX_SCALE = 40f
    const val SNAP_ANGLE_THRESHOLD = 6f
    /** Distance to engage snap guides (template px). */
    const val SNAP_DISTANCE_PX = 8f
    /** Extra distance required to break free once snapped. */
    const val SNAP_RELEASE_PX = 22f
    /** Per-frame drag speed above which snap is skipped (prevents stickiness). */
    const val SNAP_VELOCITY_BREAK_PX = 14f
    const val SNAP_EDGE_FACTOR = 0.8f

    // Padding (px) quanh bounding box hien thi — box khong sat mep anh
    const val BB_PADDING_PX = 0f

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
    data object BodyInner : HandleZone()
    data object Rotate : HandleZone()
    sealed class Corner : HandleZone() {
        data object TL : Corner()
        data object TR : Corner()
        data object BL : Corner()
        data object BR : Corner()
    }
    sealed class Edge : HandleZone() {
        data object Left : Edge()
        data object Right : Edge()
        data object Top : Edge()
        data object Bottom : Edge()
    }
}

enum class GestureMode { IDLE, DRAG, SCALE_CORNER, SCALE_EDGE, ROTATE, PINCH }

data class CachedDimensions(
    val handleVisualRadiusPx: Float,
    val handleRadiusPx: Float,
    val touchRadiusPx: Float,
    val rotateLinePx: Float,
    val rotateHandleOffsetPx: Float,
    val borderStrokePx: Float,
    val rotateRadiusPx: Float,
    val rotateRadiusActivePx: Float,
    val crosshairSizePx: Float,
    val edgeHandleWidthPx: Float,
    val edgeHandleHeightPx: Float,
    val handleTouchPaddingPx: Float,
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
    onBodyClick: () -> Unit = {},
    onBodyDoubleTap: () -> Unit = {},
    showBoundingBox: Boolean = true,
    onBoundingBoxVisible: (Boolean) -> Unit = {},
    isLocked: Boolean = false,
    otherLayers: List<EditorLayer> = emptyList(),
    isInlineEditing: Boolean = false,
    /** Text label: only TL scale + right-edge width handles (Canva-style). */
    minimalTextHandles: Boolean = false,
    /** Extra top space (px) reserved for the inline text move handle hit target. */
    inlineEditTopInsetPx: Float = 0f,
    /** Tap or drag inside the text frame while inline editing — reposition caret / selection. */
    onInlineTextPointer: (tapStart: Offset, tapEnd: Offset?, isDragSelect: Boolean) -> Unit = { _, _, _ -> },
    onGestureActiveChanged: (Boolean) -> Unit = {}
) {
    if (contentWidth <= 0f || contentHeight <= 0f || displayScale <= 0f) {
        return
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentViewport by rememberUpdatedState(viewport)
    val currentContentWidth by rememberUpdatedState(contentWidth)
    val currentContentHeight by rememberUpdatedState(contentHeight)
    val currentDisplayScale by rememberUpdatedState(displayScale)
    val currentOnGesture by rememberUpdatedState(onGesture)
    val currentOnGestureEnd by rememberUpdatedState(onGestureEnd)
    val currentOnBodyClick by rememberUpdatedState(onBodyClick)
    val currentOnBodyDoubleTap by rememberUpdatedState(onBodyDoubleTap)
    val currentOnBoundingBoxVisible by rememberUpdatedState(onBoundingBoxVisible)
    val currentTemplateSize by rememberUpdatedState(templateSize)
    val currentOtherLayers by rememberUpdatedState(otherLayers)
    val currentIsInlineEditing by rememberUpdatedState(isInlineEditing)
    val currentMinimalTextHandles by rememberUpdatedState(minimalTextHandles)
    val currentOnInlineTextPointer by rememberUpdatedState(onInlineTextPointer)
    val currentOnGestureActiveChanged by rememberUpdatedState(onGestureActiveChanged)

    fun overlayCenter(width: Float, height: Float): Offset {
        // Geometric center of the overlay box. The overlay is center-aligned with the
        // text content; shifting Y for the move-handle inset misaligned caret hit-tests.
        return Offset(width / 2f, height / 2f)
    }

    val dimensions = remember(density) {
        CachedDimensions(
            handleVisualRadiusPx = with(density) { EditorDims.HandleVisualRadiusDp.toPx() },
            handleRadiusPx = with(density) { EditorDims.HandleRadiusDp.toPx() },
            touchRadiusPx = with(density) { EditorDims.TouchRadiusDp.toPx() },
            rotateLinePx = with(density) { EditorDims.RotateLineDp.toPx() },
            rotateHandleOffsetPx = with(density) { EditorDims.RotateHandleOffsetDp.toPx() },
            borderStrokePx = with(density) { EditorDims.BorderStrokeDp.toPx() },
            rotateRadiusPx = with(density) { EditorDims.RotateRadiusDp.toPx() },
            rotateRadiusActivePx = with(density) { EditorDims.RotateRadiusActiveDp.toPx() },
            crosshairSizePx = with(density) { EditorDims.CrosshairSizeDp.toPx() },
            edgeHandleWidthPx = with(density) { 5.dp.toPx() },
            edgeHandleHeightPx = with(density) { 16.dp.toPx() },
            handleTouchPaddingPx = with(density) { EditorDims.HandleTouchPaddingDp.toPx() },
        )
    }

    val compositionTime = remember { System.currentTimeMillis() }

    var gestureMode by remember { mutableStateOf(GestureMode.IDLE) }
    var activeHandle by remember { mutableStateOf<HandleZone>(HandleZone.None) }
    var showSnap by remember { mutableStateOf(false) }
    var snapLines by remember { mutableStateOf<List<SnapLine>>(emptyList()) }

    val borderColor by animateColorAsState(
        targetValue = when (gestureMode) {
            GestureMode.DRAG -> EditorColors.BorderDrag
            GestureMode.SCALE_CORNER -> EditorColors.BorderScale
            GestureMode.SCALE_EDGE -> EditorColors.BorderScale
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
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val gestureModifier = if (showBoundingBox && !isLocked) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                var hasMoved = false
                var pastTouchSlop = false
                val touchSlop = viewConfiguration.touchSlop
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = overlayCenter(size.width.toFloat(), size.height.toFloat())

                    val screenW = currentContentWidth * currentViewport.scale * currentDisplayScale + 2 * EditorConfig.BB_PADDING_PX
                    val screenH = currentContentHeight * currentViewport.scale * currentDisplayScale + 2 * EditorConfig.BB_PADDING_PX

                    activeHandle = detectHandleRotated(
                        touch = down.position,
                        center = center,
                        screenW = screenW,
                        screenH = screenH,
                        rotation = currentViewport.rotation,
                        handleRadius = dimensions.handleRadiusPx,
                        rotateOffset = dimensions.rotateLinePx,
                        rotateRadiusPx = dimensions.rotateRadiusPx,
                        rotateHandleOffset = dimensions.rotateHandleOffsetPx,
                        lockAspectRatio = lockAspectRatio,
                        edgeHandleWidthPx = dimensions.edgeHandleWidthPx,
                        edgeHandleHeightPx = dimensions.edgeHandleHeightPx,
                        handleTouchPaddingPx = dimensions.handleTouchPaddingPx,
                        isInlineEditing = currentIsInlineEditing,
                        minimalTextHandles = currentMinimalTextHandles,
                    )

                    gestureMode = when (activeHandle) {
                        HandleZone.Body -> GestureMode.DRAG
                        HandleZone.BodyInner -> GestureMode.DRAG
                        HandleZone.Rotate -> GestureMode.ROTATE
                        is HandleZone.Corner -> GestureMode.SCALE_CORNER
                        is HandleZone.Edge -> GestureMode.SCALE_EDGE
                        HandleZone.None -> GestureMode.IDLE
                    }

                    if (gestureMode != GestureMode.IDLE && !showBoundingBox) {
                        currentOnBoundingBoxVisible(true)
                    }

                    if (gestureMode == GestureMode.IDLE) {
                        if (currentIsInlineEditing) {
                            down.consume()
                            val startPos = down.position
                            val pointerId = down.id
                            var currentPos = startPos
                            var isDragSelect = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null || !change.pressed) {
                                    currentOnInlineTextPointer(
                                        startPos,
                                        if (isDragSelect) currentPos else null,
                                        isDragSelect,
                                    )
                                    break
                                }
                                change.consume()
                                currentPos = change.position
                                if (!isDragSelect &&
                                    (currentPos - startPos).getDistance() > touchSlop
                                ) {
                                    isDragSelect = true
                                }
                            }
                        }
                        return@awaitEachGesture
                    }

                    currentOnGestureActiveChanged(true)
                    val isBodyDrag = gestureMode == GestureMode.DRAG
                    down.consume()
                    if (!isBodyDrag) {
                        performDebouncedHaptic(haptic, lastHapticTime) { lastHapticTime = it }
                    }

                    // ── LOCAL VARIABLES ──
                    var localStartTouch = down.position
                    var localLastSnappedAngle = Float.NaN

                    // Rotation-specific locals
                    var localRotateStartAngleRad = 0f
                    var localRotateStartRotation = 0f

                    // Scale-specific locals
                    var localScaleStartScale = 1f
                    var localScaleHandle: HandleZone.Corner? = null

                    // Incremental tracking locals
                    var lastSentScale = currentViewport.scale
                    var lastSentRotation = currentViewport.rotation

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

                    // ── MAIN GESTURE LOOP ──
                    // Main pass: consume before canvas-level transform gestures on ancestors.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.isEmpty()) break

                        // Dynamic layout-shift correction relative to the start center
                        val currentCenter = overlayCenter(size.width.toFloat(), size.height.toFloat())
                        val correction = center - currentCenter

                        // Two-finger pinch zooms the canvas, not the object — exit object gesture.
                        if (pressed.size >= 2 && !currentIsInlineEditing) {
                            if (hasMoved) {
                                showSnap = false
                                snapLines = emptyList()
                                currentOnGestureEnd()
                            }
                            break
                        }

                        when (gestureMode) {
                            GestureMode.PINCH -> { /* Pinch is canvas-level only */ }

                            GestureMode.DRAG -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val dragDistance = (change.position - localStartTouch).getDistance()
                                if (!pastTouchSlop) {
                                    if (dragDistance < touchSlop) continue
                                    pastTouchSlop = true
                                    performDebouncedHaptic(haptic, lastHapticTime) { lastHapticTime = it }
                                }

                                val screenDelta = change.position - change.previousPosition
                                val templateDelta = Offset(
                                    screenDelta.x / currentDisplayScale,
                                    screenDelta.y / currentDisplayScale
                                )

                                // 1:1 finger follow — snap guides are visual-only (never lock position).
                                val tentativeOffset = currentViewport.offset + templateDelta
                                val scaledContentSize = IntSize(
                                    (currentContentWidth * currentViewport.scale).toInt(),
                                    (currentContentHeight * currentViewport.scale).toInt()
                                )
                                val guide = calculateSnapGuidesOnly(
                                    offset = tentativeOffset,
                                    contentSize = scaledContentSize,
                                    templateSize = currentTemplateSize,
                                    otherLayers = currentOtherLayers,
                                )
                                showSnap = guide.isNotEmpty()
                                snapLines = guide

                                change.consume()
                                hasMoved = true

                                currentOnGesture(GestureDelta(pan = templateDelta))
                            }

                            GestureMode.ROTATE -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val adjustedPos = change.position + correction
                                val currentAngleRad = atan2(
                                    adjustedPos.y - center.y,
                                    adjustedPos.x - center.x
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

                                val rotationDelta = snappedRotation - lastSentRotation
                                lastSentRotation = snappedRotation

                                currentOnGesture(GestureDelta(
                                    rotation = rotationDelta
                                ))
                            }

                            GestureMode.SCALE_CORNER -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val handle = localScaleHandle ?: continue
                                val adjustedCurrentTouch = change.position + correction
                                val newScale = calculateRotatedScale(
                                    handle = handle,
                                    center = center,
                                    startTouch = localStartTouch,
                                    currentTouch = adjustedCurrentTouch,
                                    startScale = localScaleStartScale,
                                    rotation = currentViewport.rotation,
                                    screenW = screenW,
                                    screenH = screenH
                                )

                                val scaleFactor = newScale / lastSentScale
                                lastSentScale = newScale

                                change.consume()
                                hasMoved = true

                                currentOnGesture(GestureDelta(
                                    scale = scaleFactor,
                                    pan = Offset.Zero,
                                ))
                            }

                            GestureMode.SCALE_EDGE -> {
                                val change = pressed.firstOrNull() ?: continue
                                if (!change.positionChanged()) continue

                                val handle = activeHandle as? HandleZone.Edge ?: continue
                                val adjustedCurrentTouch = change.position + correction

                                val localPrevious = inverseRotatePoint(change.previousPosition + correction, center, currentViewport.rotation)
                                val localCurrent = inverseRotatePoint(adjustedCurrentTouch, center, currentViewport.rotation)

                                var dwTemplate = 0f
                                var dhTemplate = 0f
                                var localPanTemplate = Offset.Zero

                                when (handle) {
                                    HandleZone.Edge.Right -> {
                                        val dw = localCurrent.x - localPrevious.x
                                        dwTemplate = dw / currentDisplayScale
                                        localPanTemplate = Offset(dwTemplate / 2f, 0f)
                                    }
                                    HandleZone.Edge.Left -> {
                                        val dw = localPrevious.x - localCurrent.x
                                        dwTemplate = dw / currentDisplayScale
                                        localPanTemplate = Offset(-dwTemplate / 2f, 0f)
                                    }
                                    HandleZone.Edge.Bottom -> {
                                        val dh = localCurrent.y - localPrevious.y
                                        dhTemplate = dh / currentDisplayScale
                                        localPanTemplate = Offset(0f, dhTemplate / 2f)
                                    }
                                    HandleZone.Edge.Top -> {
                                        val dh = localPrevious.y - localCurrent.y
                                        dhTemplate = dh / currentDisplayScale
                                        localPanTemplate = Offset(0f, -dhTemplate / 2f)
                                    }
                                }

                                val rotationRad = Math.toRadians(currentViewport.rotation.toDouble())
                                val cosR = cos(rotationRad).toFloat()
                                val sinR = sin(rotationRad).toFloat()
                                val panTemplate = Offset(
                                    localPanTemplate.x * cosR - localPanTemplate.y * sinR,
                                    localPanTemplate.x * sinR + localPanTemplate.y * cosR
                                )

                                change.consume()
                                hasMoved = true

                                currentOnGesture(GestureDelta(
                                    pan = panTemplate,
                                    deltaWidth = dwTemplate,
                                    deltaHeight = dhTemplate
                                ))
                            }

                            GestureMode.IDLE -> { }
                        }
                    }

                    if (hasMoved) {
                        showSnap = false
                        snapLines = emptyList()
                        currentOnGestureEnd()
                    } else {
                        // Click or double-tap on Bounding Box body
                        if (gestureMode == GestureMode.DRAG) {
                            val tapTime = System.currentTimeMillis()
                            if (tapTime - lastTapTime < 300L) {
                                currentOnBodyDoubleTap()
                            } else {
                                if (System.currentTimeMillis() - compositionTime > 300L) {
                                    currentOnBodyClick()
                                }
                            }
                            lastTapTime = tapTime
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Coroutine was cancelled (e.g. showBoundingBox became false mid-gesture)
                    if (hasMoved || gestureMode != GestureMode.IDLE) {
                        showSnap = false
                        snapLines = emptyList()
                        currentOnGestureEnd()
                    }
                    throw e
                } finally {
                    // Always reset gesture state when scope exits for any reason
                    gestureMode = GestureMode.IDLE
                    activeHandle = HandleZone.None
                    showSnap = false
                    snapLines = emptyList()
                    currentOnGestureActiveChanged(false)
                }
            }
        }
    } else {
        Modifier
    }

    val tooltipText by remember(viewport.scale, viewport.rotation, contentWidth, contentHeight, gestureMode) {
        derivedStateOf {
            if (gestureMode == GestureMode.IDLE) ""
            else if (gestureMode == GestureMode.ROTATE) {
                "${viewport.rotation.roundToInt()}°"
            } else {
                "${(contentWidth * viewport.scale).roundToInt()} × ${(contentHeight * viewport.scale).roundToInt()} px"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(gestureModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = overlayCenter(size.width, size.height)
            val cx = center.x
            val cy = center.y

            val screenW = contentWidth * viewport.scale * displayScale
            val screenH = contentHeight * viewport.scale * displayScale
            // Them 10px padding quanh bounding box de box khong sat mep anh
            val hw = screenW / 2f + EditorConfig.BB_PADDING_PX
            val hh = screenH / 2f + EditorConfig.BB_PADDING_PX

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
                    isGestureActive = gestureMode != GestureMode.IDLE,
                    isLocked = isLocked,
                    lockAspectRatio = lockAspectRatio,
                    isInlineEditing = currentIsInlineEditing,
                    minimalTextHandles = currentMinimalTextHandles
                )

                if (tooltipText.isNotEmpty()) {
                    drawTooltip(
                        cx = cx,
                        cy = cy,
                        hh = hh,
                        text = tooltipText,
                        density = density.density
                    )
                }
            }
        }
    }
}

// ============ Debounced Haptic ============

