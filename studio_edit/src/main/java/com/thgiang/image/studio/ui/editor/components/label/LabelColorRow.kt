package com.thgiang.image.studio.ui.editor.components.label

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
internal fun LabelColorRow(
    currentArgb: Int,
    palette: List<Color>,
    onSelectColor: (Int) -> Unit,
    onCustomColorClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        palette.forEach { color ->
            val argb = color.toArgb()
            val isSelected = currentArgb == argb
            Canvas(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelectColor(argb) }
                    )
            ) {
                val width = size.width
                val height = size.height
                val radius = width / 2f
                val center = Offset(radius, radius)

                // 1. Draw base metallic sphere (silver/gray radial gradient)
                val baseGradient = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFE2E2E2),
                        Color(0xFF8C8C8C)
                    ),
                    center = Offset(width * 0.35f, height * 0.35f),
                    radius = width * 0.8f
                )
                drawCircle(
                    brush = baseGradient,
                    radius = radius,
                    center = center
                )

                // 2. Draw color semicircle on the left half (start angle 90 to 270 degrees)
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true
                )

                // 3. Draw 3D shading overlay to model depth (radial shadow from bottom-right)
                val shadowGradient = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = 0.4f)
                    ),
                    center = Offset(width * 0.35f, height * 0.35f),
                    radius = radius * 1.1f
                )
                drawCircle(
                    brush = shadowGradient,
                    radius = radius,
                    center = center
                )

                // 4. Draw glossy reflection highlight at the top-left
                val highlightGradient = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.3f, height * 0.3f),
                    radius = radius * 0.45f
                )
                drawCircle(
                    brush = highlightGradient,
                    radius = radius * 0.45f,
                    center = Offset(width * 0.3f, height * 0.3f)
                )

                // 5. Draw selection border or subtle boundary outline
                if (isSelected) {
                    val strokeWidth = 2.5.dp.toPx()
                    drawCircle(
                        color = tokens.accent,
                        radius = radius - strokeWidth / 2f,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    val strokeWidth = 0.5.dp.toPx()
                    drawCircle(
                        color = tokens.borderSubtle,
                        radius = radius - strokeWidth / 2f,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .size(38.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCustomColorClick
                )
        ) {
            val width = size.width
            val height = size.height
            val radius = width / 2f
            val center = Offset(radius, radius)

            // 1. Draw base metallic sphere (silver/gray radial gradient)
            val baseGradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFE2E2E2),
                    Color(0xFF8C8C8C)
                ),
                center = Offset(width * 0.35f, height * 0.35f),
                radius = width * 0.8f
            )
            drawCircle(
                brush = baseGradient,
                radius = radius,
                center = center
            )

            // 2. Draw rainbow sweep gradient semicircle on the left half
            val rainbowBrush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
                    Color.Green, Color.Yellow, Color.Red
                ),
                center = center
            )
            drawArc(
                brush = rainbowBrush,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true
            )

            // 3. Draw 3D shading overlay
            val shadowGradient = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.05f),
                    Color.Black.copy(alpha = 0.4f)
                ),
                center = Offset(width * 0.35f, height * 0.35f),
                radius = radius * 1.1f
            )
            drawCircle(
                brush = shadowGradient,
                radius = radius,
                center = center
            )

            // 4. Draw glossy reflection highlight at the top-left
            val highlightGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(width * 0.3f, height * 0.3f),
                radius = radius * 0.45f
            )
            drawCircle(
                brush = highlightGradient,
                radius = radius * 0.45f,
                center = Offset(width * 0.3f, height * 0.3f)
            )

            // 5. Draw subtle boundary outline
            val strokeWidth = 0.5.dp.toPx()
            drawCircle(
                color = tokens.borderSubtle,
                radius = radius - strokeWidth / 2f,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

