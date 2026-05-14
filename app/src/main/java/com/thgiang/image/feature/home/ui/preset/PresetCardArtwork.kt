package com.thgiang.image.feature.home.ui.preset
import com.thgiang.image.core.model.PresetStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
internal fun PresetCardArtwork(
    style: PresetStyle,
    ai: Color,
    modifier: Modifier = Modifier
) {
    val baseBrush = when (style) {
        PresetStyle.NOIR -> Brush.linearGradient(
            colors = listOf(Color(0xFF0D1019), Color(0xFF1A1C24), Color(0xFF111317))
        )
        PresetStyle.CLEAN -> Brush.linearGradient(
            colors = listOf(Color(0xFFFCFCFD), Color(0xFFF1F3F7), Color(0xFFE9EDF3))
        )
        PresetStyle.AURORA -> Brush.linearGradient(
            colors = listOf(Color(0xFF0B1020), Color(0xFF1A1C3A), Color(0xFF1E3154))
        )
        PresetStyle.DUOTONE -> Brush.linearGradient(
            colors = listOf(Color(0xFF2B1F3E), Color(0xFF5F4B8B), Color(0xFF00B5D8))
        )
        PresetStyle.NEON_GRID -> Brush.linearGradient(
            colors = listOf(Color(0xFF040912), Color(0xFF0A1325), Color(0xFF111F37))
        )
        PresetStyle.LIQUID_GLASS -> Brush.linearGradient(
            colors = listOf(Color(0xFFF6F4FF), Color(0xFFEAF2FF), Color(0xFFF7F9FF))
        )
        PresetStyle.SUNSET_FILM -> Brush.linearGradient(
            colors = listOf(Color(0xFF2A122A), Color(0xFF8A3E6B), Color(0xFFF09862))
        )
        PresetStyle.CARBON_X -> Brush.linearGradient(
            colors = listOf(Color(0xFF0A0A0D), Color(0xFF181A20), Color(0xFF101116))
        )
        PresetStyle.ROSE_GARDEN -> Brush.linearGradient(
            colors = listOf(Color(0xFF5B2C8F), Color(0xFFC0486C), Color(0xFFF4A261)),
            start = Offset.Zero, end = Offset.Infinite
        )
        PresetStyle.PEACH_SKY -> Brush.linearGradient(
            colors = listOf(Color(0xFF667eea), Color(0xFF764ba2), Color(0xFFF093FB)),
            start = Offset.Zero, end = Offset.Infinite
        )
        PresetStyle.GOLDEN_SUNSET -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF6B6B), Color(0xFFFFE66D), Color(0xFF4ECDC4)),
            start = Offset.Zero, end = Offset.Infinite
        )
        PresetStyle.LAVENDER_DAWN -> Brush.linearGradient(
            colors = listOf(Color(0xFFa18cd1), Color(0xFFfbc2eb), Color(0xFFf6d365)),
            start = Offset.Zero, end = Offset.Infinite
        )
        PresetStyle.AQUA_BREEZE -> Brush.linearGradient(
            colors = listOf(Color(0xFF4facfe), Color(0xFF00f2fe), Color(0xFF43e97b)),
            start = Offset.Zero, end = Offset.Infinite
        )
    }

    Box(
        modifier = modifier
            .background(baseBrush)
            .drawWithContent {
                drawContent()
                when (style) {
                    PresetStyle.NOIR -> {
                        val step = size.width / 6f
                        for (i in -2..8) {
                            val x = i * step
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(x, 0f),
                                end = Offset(x - size.height, size.height),
                                strokeWidth = 1.1f
                            )
                        }
                    }
                    PresetStyle.CLEAN -> {
                        val lineColor = Color(0xFF9EA8B8).copy(alpha = 0.22f)
                        for (i in 1..3) {
                            val y = size.height * (i / 4f)
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                        }
                    }
                    PresetStyle.NEON_GRID -> {
                        val grid = Color(0xFF3ED8FF).copy(alpha = 0.30f)
                        val stepX = size.width / 5f
                        val stepY = size.height / 4f
                        for (i in 1..4) {
                            val x = i * stepX
                            drawLine(
                                color = grid,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f
                            )
                        }
                        for (i in 1..3) {
                            val y = i * stepY
                            drawLine(
                                color = grid,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                        }
                    }
                    PresetStyle.CARBON_X -> {
                        val step = size.width / 7f
                        for (i in -1..8) {
                            val x = i * step
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(x, 0f),
                                end = Offset(x + size.height * 0.7f, size.height),
                                strokeWidth = 1.2f
                            )
                        }
                        drawRect(
                            color = ai.copy(alpha = 0.32f),
                            topLeft = Offset(size.width * 0.55f, 0f),
                            size = Size(size.width * 0.08f, size.height)
                        )
                    }
                    PresetStyle.ROSE_GARDEN -> {
                        val step = size.width / 8f
                        for (i in -1..9) {
                            val x = i * step
                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(x, 0f),
                                end = Offset(x + size.height * 0.5f, size.height),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                    PresetStyle.PEACH_SKY -> {
                        val cx = size.width / 2
                        drawCircle(
                            color = Color.White.copy(alpha = 0.20f),
                            radius = size.minDimension * 0.15f,
                            center = Offset(cx - 12f, size.height * 0.25f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f),
                            radius = size.minDimension * 0.10f,
                            center = Offset(cx + 20f, size.height * 0.35f)
                        )
                    }
                    PresetStyle.GOLDEN_SUNSET -> {
                        val step = size.width / 5f
                        for (i in 0..5) {
                            val x = i * step
                            drawLine(
                                color = Color.White.copy(alpha = 0.10f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f
                            )
                        }
                    }
                    PresetStyle.LAVENDER_DAWN -> {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = size.minDimension * 0.12f,
                            center = Offset(cx - 8f, cy - 6f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f),
                            radius = size.minDimension * 0.08f,
                            center = Offset(cx + 16f, cy + 10f)
                        )
                    }
                    PresetStyle.AQUA_BREEZE -> {
                        val stepX = size.width / 6f
                        val stepY = size.height / 4f
                        val grid = Color.White.copy(alpha = 0.15f)
                        for (i in 1..5) {
                            val x = i * stepX
                            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
                        }
                        for (i in 1..3) {
                            val y = i * stepY
                            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
                        }
                    }
                    else -> Unit
                }
            }
    ) {
        when (style) {
            PresetStyle.AURORA -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF5AEEFF).copy(alpha = 0.55f), Color.Transparent),
                                center = Offset(28f, 18f),
                                radius = 70f
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF8A7BFF).copy(alpha = 0.50f), Color.Transparent),
                                center = Offset(88f, 56f),
                                radius = 72f
                            )
                        )
                )
            }
            PresetStyle.DUOTONE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFB870).copy(alpha = 0.55f), Color.Transparent),
                                center = Offset(18f, 20f),
                                radius = 48f
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF63F0FF).copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(92f, 46f),
                                radius = 58f
                            )
                        )
                )
            }
            PresetStyle.LIQUID_GLASS -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .offset(x = 30.dp, y = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.85f), Color(0xFFDBE8FF).copy(alpha = 0.35f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .offset(x = 8.dp, y = 32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFB8DCFF).copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                )
            }
            PresetStyle.SUNSET_FILM -> {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .offset(x = 8.dp, y = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFFD087).copy(alpha = 0.85f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF3B1D3F).copy(alpha = 0.45f))
                            )
                        )
                )
            }
            else -> Unit
        }
    }
}




