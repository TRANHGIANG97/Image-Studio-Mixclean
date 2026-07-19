package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasDoubleTapZoomTest {

    private val centerX = 400f
    private val centerY = 300f
    private val tapX = 500f
    private val tapY = 350f

    @Test
    fun `at 100 percent zooms in by 50 percent`() {
        val target = resolveCanvasDoubleTapTarget(
            currentScale = 1f,
            currentOffset = Offset.Zero,
            tapX = tapX,
            tapY = tapY,
            viewportCenterX = centerX,
            viewportCenterY = centerY,
        )
        assertEquals(1.5f, target.scale, 0.001f)
    }

    @Test
    fun `between 50 and 100 percent adds 50 percent points`() {
        val target = resolveCanvasDoubleTapTarget(
            currentScale = 0.8f,
            currentOffset = Offset.Zero,
            tapX = tapX,
            tapY = tapY,
            viewportCenterX = centerX,
            viewportCenterY = centerY,
        )
        assertEquals(1.3f, target.scale, 0.001f)
    }

    @Test
    fun `at exactly 50 percent resets to 100 percent`() {
        val target = resolveCanvasDoubleTapTarget(
            currentScale = 0.5f,
            currentOffset = Offset(40f, 20f),
            tapX = tapX,
            tapY = tapY,
            viewportCenterX = centerX,
            viewportCenterY = centerY,
        )
        assertEquals(1f, target.scale, 0.001f)
        assertEquals(Offset.Zero, target.offset)
    }

    @Test
    fun `below 50 percent resets to 100 percent`() {
        val target = resolveCanvasDoubleTapTarget(
            currentScale = 0.3f,
            currentOffset = Offset(80f, -30f),
            tapX = tapX,
            tapY = tapY,
            viewportCenterX = centerX,
            viewportCenterY = centerY,
        )
        assertEquals(1f, target.scale, 0.001f)
        assertEquals(Offset.Zero, target.offset)
    }

    @Test
    fun `above 100 percent resets to default viewport`() {
        val target = resolveCanvasDoubleTapTarget(
            currentScale = 1.8f,
            currentOffset = Offset(120f, -60f),
            tapX = tapX,
            tapY = tapY,
            viewportCenterX = centerX,
            viewportCenterY = centerY,
        )
        assertEquals(1f, target.scale, 0.001f)
        assertEquals(Offset.Zero, target.offset)
    }

    @Test
    fun `zoom in preserves tap point on screen`() {
        val currentOffset = Offset(10f, -5f)
        val target = resolveCanvasDoubleTapTarget(
            currentScale = 1f,
            currentOffset = currentOffset,
            tapX = tapX,
            tapY = tapY,
            viewportCenterX = centerX,
            viewportCenterY = centerY,
        )
        val before = screenPointFromArtboard(
            artboardX = (tapX - centerX - currentOffset.x) / 1f,
            artboardY = (tapY - centerY - currentOffset.y) / 1f,
            scale = 1f,
            offset = currentOffset,
        )
        val after = screenPointFromArtboard(
            artboardX = (tapX - centerX - currentOffset.x) / 1f,
            artboardY = (tapY - centerY - currentOffset.y) / 1f,
            scale = target.scale,
            offset = target.offset,
        )
        assertEquals(before.x, after.x, 0.5f)
        assertEquals(before.y, after.y, 0.5f)
    }

    private fun screenPointFromArtboard(
        artboardX: Float,
        artboardY: Float,
        scale: Float,
        offset: Offset,
    ): Offset {
        return Offset(
            centerX + offset.x + artboardX * scale,
            centerY + offset.y + artboardY * scale,
        )
    }
}
