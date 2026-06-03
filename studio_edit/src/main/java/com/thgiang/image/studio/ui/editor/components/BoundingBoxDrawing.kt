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
    isGestureActive: Boolean,
    isLocked: Boolean = false
) {
    withTransform({
        rotate(degrees = rotation, pivot = Offset(cx, cy))
    }) {
        val pathEffect = when {
            isLocked -> PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            gestureMode == GestureMode.DRAG -> PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
            gestureMode == GestureMode.SCALE_CORNER || gestureMode == GestureMode.PINCH -> PathEffect.dashPathEffect(floatArrayOf(8f, 4f, 2f, 4f), 0f)
            else -> null
        }

        drawRect(
            color = if (isLocked) Color.Gray.copy(alpha = 0.5f) else borderColor,
            topLeft = Offset(cx - hw, cy - hh),
            size = Size(screenW, screenH),
            style = Stroke(width = dimensions.borderStrokePx, pathEffect = pathEffect)
        )

        if (!isLocked) {
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

                // Ambient shadow behind the handle
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = radius + 2.dp.toPx(),
                    center = pos
                )

                // Accent border glow when active
                if (isActive) {
                    drawCircle(
                        color = EditorColors.HandleActive.copy(alpha = 0.4f),
                        radius = radius + 4.dp.toPx(),
                        center = pos
                    )
                }

                // Inner filled circle
                drawCircle(color = color, radius = radius, center = pos)

                // Sleek dark stroke
                drawCircle(
                    color = EditorColors.HandleStroke,
                    radius = radius,
                    center = pos,
                    style = Stroke(dimensions.borderStrokePx)
                )

                // White highlight inside when active
                if (isActive) {
                    drawCircle(
                        color = Color.White,
                        radius = radius * 0.4f,
                        center = pos
                    )
                }
            }

            // Rotation handle
            val rotPos = Offset(cx, cy + hh + dimensions.rotateLinePx + dimensions.rotateHandleOffsetPx)

            val isRotating = gestureMode == GestureMode.ROTATE
            val rotColor = if (isRotating) EditorColors.RotateHandleActive else EditorColors.RotateHandle
            val rotR = if (isRotating) dimensions.rotateRadiusActivePx else dimensions.rotateRadiusPx

            // Ambient soft shadow behind the entire rotation handle
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = rotR + 3.dp.toPx(),
                center = rotPos
            )

            // Accent outer glow
            val glowColor = if (isRotating) rotColor.copy(alpha = 0.45f) else rotColor.copy(alpha = 0.2f)
            drawCircle(
                color = glowColor,
                radius = rotR + 4.dp.toPx(),
                center = rotPos
            )

            // Glossy lens/glass gradient background
            val rotBgBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = if (isRotating) {
                    listOf(rotColor.copy(alpha = 0.95f), EditorColors.HandleStroke.copy(alpha = 0.9f))
                } else {
                    listOf(EditorColors.HandleStroke.copy(alpha = 0.95f), EditorColors.HandleStroke)
                },
                center = rotPos,
                radius = rotR
            )
            drawCircle(
                brush = rotBgBrush,
                radius = rotR,
                center = rotPos
            )

            // Draw accent border
            drawCircle(
                color = rotColor,
                radius = rotR,
                center = rotPos,
                style = Stroke(width = 2.dp.toPx())
            )

            // Rotate handle icon — arc arrow in white
            drawRotatingHandleIcon(
                center = rotPos,
                radius = rotR,
                rotation = rotation,
                color = Color.White
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
