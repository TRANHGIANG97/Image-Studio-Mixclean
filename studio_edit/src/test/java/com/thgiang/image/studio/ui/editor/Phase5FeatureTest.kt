package com.thgiang.image.studio.ui.editor

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.canvas.CropMath
import com.thgiang.image.studio.ui.editor.model.CropRatio
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorProduct
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.SelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {

    @Test
    fun `clampOffset stays within max pan`() {
        val layer = imageLayer(baseWidth = 800, baseHeight = 600)
        val clamped = CropMath.clampOffset(layer, 500f, 500f)
        val (maxX, maxY) = CropMath.maxPan(layer)
        assertTrue(kotlin.math.abs(clamped.x) <= maxX + 0.01f)
        assertTrue(kotlin.math.abs(clamped.y) <= maxY + 0.01f)
    }

    @Test
    fun `applyPanDelta accumulates offset`() {
        val layer = imageLayer().copy(cropOffsetX = 0f, cropOffsetY = 0f)
        val result = CropMath.applyPanDelta(layer, Offset(3f, 2f))
        assertEquals(0f, result.cropOffsetX, 0.01f)
        assertEquals(0f, result.cropOffsetY, 0.01f)
    }

    @Test
    fun `resetOffsetForRatio clears offset`() {
        val layer = imageLayer().copy(cropOffsetX = 20f, cropOffsetY = 15f)
        val result = CropMath.resetOffsetForRatio(layer, CropRatio.RATIO_1_1)
        assertEquals(CropRatio.RATIO_1_1, result.cropRatio)
        assertEquals(0f, result.cropOffsetX, 0.01f)
        assertEquals(0f, result.cropOffsetY, 0.01f)
    }

    private fun imageLayer(
        baseWidth: Int = 400,
        baseHeight: Int = 400,
    ) = EditorLayer(
        type = LayerType.IMAGE,
        shapeWidthPx = 300f,
        shapeHeightPx = 300f,
        cropRatio = CropRatio.RATIO_1_1,
        product = EditorProduct(
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            isBackgroundRemoved = true,
        ),
    )
}

class SelectionStateTest {

    @Test
    fun `expandGroup includes frame and label siblings`() {
        val frame = EditorLayer(id = "f", groupId = "g", groupRole = com.thgiang.image.studio.ui.editor.model.LayerGroupRole.FRAME)
        val label = EditorLayer(id = "l", groupId = "g", groupRole = com.thgiang.image.studio.ui.editor.model.LayerGroupRole.LABEL)
        val layers = listOf(frame, label)

        val expanded = SelectionState.expandGroup(layers, "f")

        assertEquals(setOf("f", "l"), expanded)
    }

    @Test
    fun `toggle adds then removes layer`() {
        val a = EditorLayer(id = "a")
        val b = EditorLayer(id = "b")
        val layers = listOf(a, b)

        val (anchor1, ids1) = SelectionState.toggle(layers, emptySet(), null, "a")
        assertEquals("a", anchor1)
        assertEquals(setOf("a"), ids1)

        val (anchor2, ids2) = SelectionState.toggle(layers, ids1, anchor1, "b")
        assertEquals("a", anchor2)
        assertEquals(setOf("a", "b"), ids2)

        val (anchor3, ids3) = SelectionState.toggle(layers, ids2, anchor2, "b")
        assertEquals("a", anchor3)
        assertEquals(setOf("a"), ids3)
    }

    @Test
    fun `isSelected respects multi select set`() {
        val a = EditorLayer(id = "a")
        val b = EditorLayer(id = "b")
        val layers = listOf(a, b)

        assertTrue(SelectionState.isSelected(layers, "a", setOf("a", "b"), "b"))
        assertFalse(SelectionState.isSelected(layers, "a", setOf("a"), "b"))
    }
}
