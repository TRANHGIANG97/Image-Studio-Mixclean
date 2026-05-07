package com.thgiang.image.core.design.components
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun TransparentBackgroundPattern(
    modifier: Modifier = Modifier,
    squareSize: Dp = 16.dp,
    lightColor: Color = Color(0xFFE0E0E0),
    darkColor: Color = Color(0xFFCFCFCF)
) {
    val density = LocalDensity.current
    val squareSizePx = remember(squareSize, density) {
        with(density) { squareSize.toPx().coerceAtLeast(1f) }
    }

    Canvas(modifier = modifier) {
        if (size.minDimension <= 0f) return@Canvas

        val cols = ceil(size.width / squareSizePx).toInt()
        val rows = ceil(size.height / squareSizePx).toInt()

        for (row in 0 until rows) {
            val y = row * squareSizePx
            val cellHeight = min(squareSizePx, size.height - y)
            if (cellHeight <= 0f) continue

            for (col in 0 until cols) {
                val x = col * squareSizePx
                val cellWidth = min(squareSizePx, size.width - x)
                if (cellWidth <= 0f) continue

                val isDarkCell = (row + col) % 2 == 1
                drawRect(
                    color = if (isDarkCell) darkColor else lightColor,
                    topLeft = Offset(x, y),
                    size = Size(cellWidth, cellHeight)
                )
            }
        }
    }
}





