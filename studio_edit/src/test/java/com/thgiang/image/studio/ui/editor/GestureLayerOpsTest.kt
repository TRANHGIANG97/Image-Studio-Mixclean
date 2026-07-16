package com.thgiang.image.studio.ui.editor

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.canvas.GestureDelta
import com.thgiang.image.studio.ui.editor.canvas.GestureLayerOps
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.LayerGroupRole
import com.thgiang.image.studio.ui.editor.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureLayerOpsTest {

    @Test
    fun `pan updates viewport offset`() {
        val layer = imageLayer()
        val delta = GestureDelta(pan = Offset(12f, -8f))

        val result = GestureLayerOps.applyDelta(layer, delta)

        assertEquals(12f, result.viewport.offsetX, 0.001f)
        assertEquals(-8f, result.viewport.offsetY, 0.001f)
    }

    @Test
    fun `scale clamps image layer viewport scale`() {
        val layer = imageLayer(viewport = EditorViewport(scale = 25f))
        val delta = GestureDelta(scale = 2f)

        val result = GestureLayerOps.applyDelta(layer, delta)

        assertEquals(GestureLayerOps.MAX_SCALE, result.viewport.scale, 0.001f)
    }

    @Test
    fun `rotation normalizes negative degrees`() {
        val layer = imageLayer(viewport = EditorViewport(rotation = 10f))
        val delta = GestureDelta(rotation = -20f)

        val result = GestureLayerOps.applyDelta(layer, delta)

        assertEquals(350f, result.viewport.rotation, 0.001f)
    }

    @Test
    fun `label layer scales via viewport like other layers`() {
        val layer = EditorLayer(
            id = "label",
            type = LayerType.SHAPE_TEXT,
            groupRole = LayerGroupRole.LABEL,
            textSizeSp = 20f,
            shapeWidthPx = 200f,
            shapeHeightPx = 80f,
        )
        val delta = GestureDelta(scale = 1.5f)

        val result = GestureLayerOps.applyDelta(layer, delta)

        assertEquals(20f, result.textSizeSp, 0.001f)
        assertEquals(200f, result.shapeWidthPx, 0.001f)
        assertEquals(80f, result.shapeHeightPx, 0.001f)
        assertEquals(1.5f, result.viewport.scale, 0.001f)
    }

    @Test
    fun `resize delta enforces minimum shape size`() {
        val layer = imageLayer(shapeWidthPx = 70f, shapeHeightPx = 65f)
        val delta = GestureDelta(deltaWidth = -20f, deltaHeight = -20f)

        val result = GestureLayerOps.applyDelta(layer, delta)

        assertEquals(60f, result.shapeWidthPx, 0.001f)
        assertEquals(45f, result.shapeHeightPx, 0.001f)
    }

    private fun imageLayer(
        viewport: EditorViewport = EditorViewport(),
        shapeWidthPx: Float = 240f,
        shapeHeightPx: Float = 100f,
    ) = EditorLayer(
        id = "image",
        type = LayerType.IMAGE,
        shapeWidthPx = shapeWidthPx,
        shapeHeightPx = shapeHeightPx,
        viewport = viewport,
    )
}
