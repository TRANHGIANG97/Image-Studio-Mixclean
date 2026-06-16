package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.*
import kotlin.math.roundToInt

/**
 * ShapeTextLayer — renders a SHAPE_TEXT EditorLayer on the canvas.
 *
 * Supports three shapes:
 *  • PILL     — fully rounded capsule
 *  • CARD     — softly rounded rectangle
 *  • TEARDROP — 3 rounded corners + a sharp pointer at the bottom-left
 *
 * The layer participates in the same gesture model as image layers
 * (drag, pinch-zoom, two-finger rotate) and shows the bounding box
 * selection ring when selected.
 */
@Composable
fun ShapeTextLayer(
    layer: EditorLayer,
    displayScale: Float,
    templateSize: IntSize,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showBoundingBox: Boolean,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var composeFontFamily by remember(layer.fontFamily) { mutableStateOf<androidx.compose.ui.text.font.FontFamily?>(null) }
    LaunchedEffect(layer.fontFamily) {
        layer.fontFamily?.let { familyName ->
            val nativeTf = com.thgiang.image.studio.util.FontDownloader.getTypeface(context, familyName)
            if (nativeTf != null) {
                composeFontFamily = androidx.compose.ui.text.font.FontFamily(nativeTf)
            }
        }
    }

    // ── Derive display dimensions from template-space shape size ──────────
    val displayW = with(density) { (layer.shapeWidthPx  * layer.viewport.scale * displayScale).toDp() }
    val displayH = with(density) { (layer.shapeHeightPx * layer.viewport.scale * displayScale).toDp() }

    // ── Centre offset in display pixels ──────────────────────────────────
    val offsetXPx = layer.viewport.offset.x * displayScale
    val offsetYPx = layer.viewport.offset.y * displayScale

    val shapeColor  = Color(layer.shapeColorArgb)
    val isShapeVisible = shapeColor.alpha > 0.01f
    val textColor   = Color(layer.textColorArgb)
    val textSizeSp  = layer.textSizeSp

    Box(
        modifier = modifier
            .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
            .size(displayW, displayH)
            .graphicsLayer {
                rotationZ  = layer.viewport.rotation
                alpha      = layer.appearance.alpha
                scaleX     = if (layer.viewport.flippedH) -1f else 1f
                scaleY     = if (layer.viewport.flippedV) -1f else 1f
            }
            .pointerInput(isLocked, layer.id) {
                if (!isLocked) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        onGesture(
                            GestureDelta(
                                pan      = pan / displayScale,
                                scale    = zoom,
                                rotation = rotation
                            )
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Shape background drawn by Compose Canvas ──────────────────────
        if (isShapeVisible) {
            ShapeBackground(
                shapeType  = layer.shapeType,
                fillColor  = shapeColor,
                modifier   = Modifier.fillMaxSize()
            )
        }

        // ── Text label ───────────────────────────────────────────────────
        Text(
            text       = layer.text,
            color      = textColor,
            fontSize   = (textSizeSp * layer.viewport.scale * displayScale).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = composeFontFamily,
            textAlign  = TextAlign.Center,
            maxLines   = 3,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // ── Selection ring ────────────────────────────────────────────────
        if (showBoundingBox) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        if (isShapeVisible) {
                            drawShapeOutline(
                                shapeType = layer.shapeType,
                                strokeColor = android.graphics.Color.parseColor("#3B82F6"),
                                strokeWidthPx = 3f * density.density
                            )
                        } else {
                            drawRect(
                                color = Color(android.graphics.Color.parseColor("#3B82F6")),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * density.density)
                            )
                        }
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShapeBackground(
    shapeType: ShapeType,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    when (shapeType) {
        ShapeType.PILL -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(fillColor)
            )
        }
        ShapeType.CARD -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(fillColor)
            )
        }
        ShapeType.TEARDROP -> {
            Box(
                modifier = modifier.drawBehind {
                    drawTeardropPath(fillColor)
                }
            )
        }
        ShapeType.CIRCLE -> {
            Box(
                modifier = modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(fillColor)
            )
        }
        ShapeType.STAR -> {
            Box(
                modifier = modifier.drawBehind {
                    drawPath(buildStarPath(size), fillColor)
                }
            )
        }
        ShapeType.HEXAGON -> {
            Box(
                modifier = modifier.drawBehind {
                    drawPath(buildHexagonPath(size), fillColor)
                }
            )
        }
    }
}

/** Draw the shape outline for the selection ring */
private fun DrawScope.drawShapeOutline(
    shapeType: ShapeType,
    strokeColor: Int,
    strokeWidthPx: Float
) {
    val strokeW = strokeWidthPx
    val paint   = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
    val color   = Color(strokeColor)

    when (shapeType) {
        ShapeType.PILL -> {
            drawRoundRect(
                color        = color,
                cornerRadius = CornerRadius(size.height / 2f),
                style        = paint
            )
        }
        ShapeType.CARD -> {
            val r = 48f // ~16 dp in px
            drawRoundRect(
                color        = color,
                cornerRadius = CornerRadius(r),
                style        = paint
            )
        }
        ShapeType.TEARDROP -> {
            drawPath(
                path  = buildTeardropPath(size),
                color = color,
                style = paint
            )
        }
        ShapeType.CIRCLE -> {
            drawOval(
                color = color,
                style = paint
            )
        }
        ShapeType.STAR -> {
            drawPath(
                path  = buildStarPath(size),
                color = color,
                style = paint
            )
        }
        ShapeType.HEXAGON -> {
            drawPath(
                path  = buildHexagonPath(size),
                color = color,
                style = paint
            )
        }
    }
}

/** Filled teardrop path — rounded top/right/bottom-right, sharp corner bottom-left */
private fun DrawScope.drawTeardropPath(fillColor: Color) {
    drawPath(
        path  = buildTeardropPath(size),
        color = fillColor
    )
}

private fun buildTeardropPath(size: Size): Path {
    val w = size.width
    val h = size.height
    val r = minOf(w, h) * 0.38f  // corner radius for the 3 rounded corners
    val ptr = h * 0.22f           // pointer depth

    return Path().apply {
        // Start from top-left rounded corner
        moveTo(r, 0f)
        // Top-right rounded corner
        lineTo(w - r, 0f)
        cubicTo(w, 0f, w, r, w, r)
        // Bottom-right rounded corner
        lineTo(w, h - r - ptr)
        cubicTo(w, h - ptr, w - r, h - ptr, w - r, h - ptr)
        // Bottom path curves to the sharp pointer at bottom-left
        lineTo(r * 0.5f, h - ptr)
        cubicTo(0f, h - ptr, 0f, h, 0f, h)
        // Sharp pointer — just a point, back up along left edge
        lineTo(0f, h - ptr - r)
        cubicTo(0f, r, r, 0f, r, 0f)
        close()
    }
}

private fun buildStarPath(size: Size): Path {
    val w = size.width
    val h = size.height
    val centerX = w / 2f
    val centerY = h / 2f
    val outerRadius = minOf(w, h) / 2f
    val innerRadius = outerRadius * 0.4f
    
    return Path().apply {
        var angle = -Math.PI / 2.0
        val angleStep = Math.PI / 5.0
        
        moveTo(
            centerX + (outerRadius * Math.cos(angle)).toFloat(),
            centerY + (outerRadius * Math.sin(angle)).toFloat()
        )
        for (i in 1..10) {
            angle += angleStep
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            lineTo(
                centerX + (radius * Math.cos(angle)).toFloat(),
                centerY + (radius * Math.sin(angle)).toFloat()
            )
        }
        close()
    }
}

private fun buildHexagonPath(size: Size): Path {
    val w = size.width
    val h = size.height
    val r = minOf(w, h) / 2f
    val centerX = w / 2f
    val centerY = h / 2f
    
    return Path().apply {
        for (i in 0..5) {
            val angle = Math.PI / 3.0 * i
            val px = centerX + (r * Math.cos(angle)).toFloat()
            val py = centerY + (r * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}
