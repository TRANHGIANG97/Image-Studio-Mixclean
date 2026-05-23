package com.thgiang.image.studio.ui.editor.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.HandleZone
import com.thgiang.image.studio.ui.editor.EditorColors
import com.thgiang.image.studio.ui.editor.EditorDims
import com.thgiang.image.studio.ui.editor.EditorConfig
import com.thgiang.image.studio.ui.editor.CachedDimensions
import com.thgiang.image.studio.ui.editor.GestureMode
import com.thgiang.image.studio.ui.editor.SnapLine
import com.thgiang.image.studio.ui.editor.SnapType
import kotlin.math.*

fun performDebouncedHaptic(
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

fun DrawScope.drawSnapLines(
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

fun DrawScope.drawRotatedOverlay(
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
fun DrawScope.drawRotatingHandleIcon(
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

fun DrawScope.drawRuleOfThirdsGrid(cx: Float, cy: Float, hw: Float, hh: Float) {
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
