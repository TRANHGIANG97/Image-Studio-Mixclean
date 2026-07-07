package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.model.EditorViewport

/** Thin edit frame around text — no resize handles (Canva ảnh 2 / 5). */
private val TextEditFrameColor = Color(0xFF7C3AED)

@Composable
fun TextEditFrameOverlay(
    contentWidth: Float,
    contentHeight: Float,
    viewport: EditorViewport,
    displayScale: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val borderStrokePx = with(density) { 1.5.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val screenW = contentWidth * viewport.scale * displayScale
        val screenH = contentHeight * viewport.scale * displayScale
        val hw = screenW / 2f
        val hh = screenH / 2f

        withTransform({
            rotate(degrees = viewport.rotation, pivot = center)
        }) {
            drawRect(
                color = TextEditFrameColor,
                topLeft = Offset(center.x - hw, center.y - hh),
                size = Size(hw * 2f, hh * 2f),
                style = Stroke(width = borderStrokePx),
            )
        }
    }
}
