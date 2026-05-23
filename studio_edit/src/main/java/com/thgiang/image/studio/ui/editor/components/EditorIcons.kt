package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun EditorBackIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.13f
        drawLine(
            color = tint,
            start = Offset(w * 0.72f, h * 0.28f),
            end = Offset(w * 0.30f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.50f),
            end = Offset(w * 0.72f, h * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.44f, h * 0.32f),
            end = Offset(w * 0.30f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.44f, h * 0.68f),
            end = Offset(w * 0.30f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun EditorUndoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.45f),
) {
    EditorCurvedArrowIcon(modifier = modifier, tint = tint, mirrored = false)
}

@Composable
fun EditorRedoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.45f),
) {
    EditorCurvedArrowIcon(modifier = modifier, tint = tint, mirrored = true)
}

@Composable
fun EditorCheckIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.12f
        drawLine(
            color = tint,
            start = Offset(w * 0.22f, h * 0.55f),
            end = Offset(w * 0.42f, h * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.42f, h * 0.76f),
            end = Offset(w * 0.78f, h * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun EditorCropIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.08f
        drawLine(tint, Offset(w * 0.24f, h * 0.20f), Offset(w * 0.24f, h * 0.58f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.24f, h * 0.20f), Offset(w * 0.62f, h * 0.20f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.38f), Offset(w * 0.38f, h * 0.80f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.80f), Offset(w * 0.80f, h * 0.80f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.62f, h * 0.38f), Offset(w * 0.62f, h * 0.80f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.62f, h * 0.38f), Offset(w * 0.80f, h * 0.38f), stroke, StrokeCap.Round)
    }
}

@Composable
fun EditorPhotoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.07f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.08f, h * 0.08f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = min(w, h) * 0.06f,
            center = Offset(w * 0.33f, h * 0.38f)
        )
        val path = Path().apply {
            moveTo(w * 0.26f, h * 0.67f)
            lineTo(w * 0.43f, h * 0.49f)
            lineTo(w * 0.54f, h * 0.61f)
            lineTo(w * 0.62f, h * 0.52f)
            lineTo(w * 0.76f, h * 0.67f)
            close()
        }
        drawPath(path = path, color = tint.copy(alpha = 0.92f))
    }
}

@Composable
fun EditorGridIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.09f
        val cellW = w * 0.18f
        val cellH = h * 0.18f
        val x1 = w * 0.24f
        val x2 = w * 0.56f
        val y1 = h * 0.24f
        val y2 = h * 0.56f
        listOf(Offset(x1, y1), Offset(x2, y1), Offset(x1, y2), Offset(x2, y2)).forEach {
            drawRoundRect(
                color = tint,
                topLeft = it,
                size = androidx.compose.ui.geometry.Size(cellW, cellH),
                cornerRadius = CornerRadius(w * 0.03f, h * 0.03f),
                style = Stroke(width = stroke)
            )
        }
    }
}

@Composable
fun EditorRotateIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.085f
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(w * 0.20f, h * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.60f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(tint, Offset(w * 0.62f, h * 0.20f), Offset(w * 0.78f, h * 0.20f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.20f), Offset(w * 0.72f, h * 0.12f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.20f), Offset(w * 0.70f, h * 0.28f), stroke, StrokeCap.Round)
    }
}

@Composable
fun EditorShadowIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.08f
        drawCircle(
            color = tint,
            radius = min(w, h) * 0.13f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = stroke)
        )
        val cx = w * 0.50f
        val cy = h * 0.50f
        val inner = min(w, h) * 0.30f
        val outer = min(w, h) * 0.40f
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45f - 90f).toDouble())
            val sx = cx + cos(angle).toFloat() * inner
            val sy = cy + sin(angle).toFloat() * inner
            val ex = cx + cos(angle).toFloat() * outer
            val ey = cy + sin(angle).toFloat() * outer
            drawLine(tint, Offset(sx, sy), Offset(ex, ey), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun EditorAngleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.07f
        drawCircle(
            color = tint,
            radius = min(w, h) * 0.28f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = stroke)
        )
        drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.72f, h * 0.30f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, h * 0.30f), Offset(w * 0.62f, h * 0.34f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, h * 0.30f), Offset(w * 0.68f, h * 0.40f), stroke, StrokeCap.Round)
    }
}

@Composable
fun EditorDistanceIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.08f
        drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.78f, h * 0.50f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.32f, h * 0.40f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.32f, h * 0.60f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.50f), Offset(w * 0.68f, h * 0.40f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.50f), Offset(w * 0.68f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

@Composable
fun EditorColorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = tint,
            radius = min(w, h) * 0.24f,
            center = Offset(w * 0.44f, h * 0.50f),
            style = Stroke(width = min(w, h) * 0.08f)
        )
        drawCircle(
            color = Color(0xFF6D4CFF),
            radius = min(w, h) * 0.09f,
            center = Offset(w * 0.68f, h * 0.60f)
        )
    }
}

@Composable
fun EditorOpacityIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.07f
        val startX = w * 0.26f
        val startY = h * 0.26f
        val cell = min(w, h) * 0.12f
        repeat(2) { row ->
            repeat(2) { col ->
                val x = startX + col * cell
                val y = startY + row * cell
                val fill = if ((row + col) % 2 == 0) tint else tint.copy(alpha = 0.18f)
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(cell, cell),
                    cornerRadius = CornerRadius(w * 0.02f, h * 0.02f)
                )
            }
        }
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.06f, h * 0.06f),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun EditorNoneIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE2E5EA),
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.08f
        drawCircle(
            color = tint,
            radius = min(w, h) * 0.24f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.70f),
            end = Offset(w * 0.70f, h * 0.30f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun EditorFlipHorizontalIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.08f
        drawLine(tint, Offset(w * 0.28f, h * 0.24f), Offset(w * 0.28f, h * 0.76f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, h * 0.24f), Offset(w * 0.72f, h * 0.76f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.50f), Offset(w * 0.46f, h * 0.36f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.50f), Offset(w * 0.46f, h * 0.64f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, h * 0.50f), Offset(w * 0.54f, h * 0.36f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, h * 0.50f), Offset(w * 0.54f, h * 0.64f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.46f, h * 0.36f), Offset(w * 0.54f, h * 0.36f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.46f, h * 0.64f), Offset(w * 0.54f, h * 0.64f), stroke, StrokeCap.Round)
    }
}

@Composable
fun EditorFlipVerticalIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.08f
        drawLine(tint, Offset(w * 0.24f, h * 0.28f), Offset(w * 0.76f, h * 0.28f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.24f, h * 0.72f), Offset(w * 0.76f, h * 0.72f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.28f), Offset(w * 0.36f, h * 0.46f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.28f), Offset(w * 0.64f, h * 0.46f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.36f, h * 0.54f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.64f, h * 0.54f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.36f, h * 0.46f), Offset(w * 0.36f, h * 0.54f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.64f, h * 0.46f), Offset(w * 0.64f, h * 0.54f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun EditorCurvedArrowIcon(
    modifier: Modifier = Modifier,
    tint: Color,
    mirrored: Boolean,
) {
    EditorGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.09f
        val path = Path().apply {
            if (!mirrored) {
                moveTo(w * 0.74f, h * 0.30f)
                quadraticBezierTo(w * 0.30f, h * 0.22f, w * 0.28f, h * 0.56f)
                lineTo(w * 0.42f, h * 0.56f)
            } else {
                moveTo(w * 0.26f, h * 0.30f)
                quadraticBezierTo(w * 0.70f, h * 0.22f, w * 0.72f, h * 0.56f)
                lineTo(w * 0.58f, h * 0.56f)
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (!mirrored) {
            drawLine(tint, Offset(w * 0.28f, h * 0.56f), Offset(w * 0.38f, h * 0.48f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.28f, h * 0.56f), Offset(w * 0.38f, h * 0.64f), stroke, StrokeCap.Round)
        } else {
            drawLine(tint, Offset(w * 0.72f, h * 0.56f), Offset(w * 0.62f, h * 0.48f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.72f, h * 0.56f), Offset(w * 0.62f, h * 0.64f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun EditorGlyphCanvas(
    modifier: Modifier = Modifier,
    tint: Color,
    drawBlock: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Canvas(modifier = modifier) {
        drawBlock()
    }
}
