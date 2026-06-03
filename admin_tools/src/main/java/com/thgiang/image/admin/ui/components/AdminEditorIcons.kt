package com.thgiang.image.admin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun AdminBackIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    AdminGlyphCanvas(modifier = modifier, tint = tint) {
        val w = size.width
        val h = size.height
        val stroke = min(w, h) * 0.10f
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.50f),
            end = Offset(w * 0.74f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.50f),
            end = Offset(w * 0.52f, h * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.50f),
            end = Offset(w * 0.52f, h * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AdminUndoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.45f),
) {
    AdminCurvedArrowIcon(modifier = modifier, tint = tint, mirrored = false)
}

@Composable
fun AdminRedoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.45f),
) {
    AdminCurvedArrowIcon(modifier = modifier, tint = tint, mirrored = true)
}

@Composable
fun AdminLottieLoader(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF2563EB)
        )
    }
}

@Composable
private fun AdminCurvedArrowIcon(
    modifier: Modifier = Modifier,
    tint: Color,
    mirrored: Boolean,
) {
    AdminGlyphCanvas(modifier = modifier, tint = tint) {
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
private fun AdminGlyphCanvas(
    modifier: Modifier = Modifier,
    tint: Color,
    drawBlock: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Canvas(modifier = modifier) {
        drawBlock()
    }
}
