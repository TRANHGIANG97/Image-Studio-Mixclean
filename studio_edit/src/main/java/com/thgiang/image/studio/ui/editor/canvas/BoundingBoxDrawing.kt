package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.canvas.*

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    isLocked: Boolean = false,
    lockAspectRatio: Boolean = true
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
            size = Size(hw * 2, hh * 2),
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

            // Edge handles (Canva-style pill handles)
            if (!lockAspectRatio) {
                val edgeHandles = listOf(
                    Offset(cx - hw, cy) to HandleZone.Edge.Left,
                    Offset(cx + hw, cy) to HandleZone.Edge.Right,
                    Offset(cx, cy - hh) to HandleZone.Edge.Top,
                    Offset(cx, cy + hh) to HandleZone.Edge.Bottom
                )

                edgeHandles.forEach { (pos, zone) ->
                    val isActive = gestureMode == GestureMode.SCALE_EDGE && activeHandle == zone
                    val isHorizontal = zone == HandleZone.Edge.Top || zone == HandleZone.Edge.Bottom
                    
                    val w = if (isHorizontal) dimensions.edgeHandleHeightPx else dimensions.edgeHandleWidthPx
                    val h = if (isHorizontal) dimensions.edgeHandleWidthPx else dimensions.edgeHandleHeightPx
                    
                    val handleColor = if (isActive) EditorColors.HandleActive else EditorColors.HandleInactive
                    
                    // Shadow behind the pill handle
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.2f),
                        topLeft = Offset(pos.x - w / 2f - 1.5f.dp.toPx(), pos.y - h / 2f - 1.5f.dp.toPx()),
                        size = Size(w + 3.dp.toPx(), h + 3.dp.toPx()),
                        cornerRadius = CornerRadius(dimensions.edgeHandleWidthPx / 2f, dimensions.edgeHandleWidthPx / 2f)
                    )

                    // Accent glow when active
                    if (isActive) {
                        drawRoundRect(
                            color = EditorColors.HandleActive.copy(alpha = 0.35f),
                            topLeft = Offset(pos.x - w / 2f - 3.dp.toPx(), pos.y - h / 2f - 3.dp.toPx()),
                            size = Size(w + 6.dp.toPx(), h + 6.dp.toPx()),
                            cornerRadius = CornerRadius(dimensions.edgeHandleWidthPx / 2f, dimensions.edgeHandleWidthPx / 2f)
                        )
                    }

                    // Main pill handle
                    drawRoundRect(
                        color = handleColor,
                        topLeft = Offset(pos.x - w / 2f, pos.y - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(dimensions.edgeHandleWidthPx / 2f, dimensions.edgeHandleWidthPx / 2f)
                    )

                    // Border stroke
                    drawRoundRect(
                        color = EditorColors.HandleStroke,
                        topLeft = Offset(pos.x - w / 2f, pos.y - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(dimensions.edgeHandleWidthPx / 2f, dimensions.edgeHandleWidthPx / 2f),
                        style = Stroke(width = dimensions.borderStrokePx)
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

fun DrawScope.drawTooltip(
    cx: Float,
    cy: Float,
    hh: Float,
    text: String,
    density: Float
) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 11f * density
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    val textWidth = paint.measureText(text)
    val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent

    val padX = 8f * density
    val padY = 5f * density

    val rectW = textWidth + padX * 2
    val rectH = textHeight + padY * 2

    val tooltipY = cy + hh + 28f * density
    val tooltipX = cx

    val rect = android.graphics.RectF(
        tooltipX - rectW / 2f,
        tooltipY - rectH / 2f,
        tooltipX + rectW / 2f,
        tooltipY + rectH / 2f
    )

    // Draw background shadow
    val shadowPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        alpha = 40
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(
        android.graphics.RectF(rect.left, rect.top + 1.5f * density, rect.right, rect.bottom + 1.5f * density),
        6f * density,
        6f * density,
        shadowPaint
    )

    // Draw dark rounded rect background
    val bgPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#1F2937") // Grey 800
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(
        rect,
        6f * density,
        6f * density,
        bgPaint
    )

    // Draw text inside
    val textY = tooltipY - (paint.fontMetrics.descent + paint.fontMetrics.ascent) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, tooltipX, textY, paint)
}
