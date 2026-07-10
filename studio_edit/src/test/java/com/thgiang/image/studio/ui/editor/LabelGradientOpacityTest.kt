package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.label.factory.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.shape.ShapeViewModelDelegate
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LabelGradientOpacityTest {

    @Test
    fun `updateShapeColor clears fillGradient`() {
        val fixture = shapeDelegateFixture()
        fixture.delegate.updateShapeColor(0x4DFFF8E1.toInt())

        val updated = fixture.readLayer()
        assertNull(updated.fillGradient)
        assertEquals(0x4DFFF8E1.toInt(), updated.shapeColorArgb)
    }

    @Test
    fun `appearance alpha update preserves fillGradient`() {
        val layer = labelLayerWithGradient()
        val originalGradient = layer.fillGradient
        val updated = layer.copy(appearance = layer.appearance.copy(alpha = 0.29f))

        assertNotNull(updated.fillGradient)
        assertEquals(originalGradient, updated.fillGradient)
        assertEquals(0.29f, updated.appearance.alpha, 0.001f)
        assertEquals(0xFFFF8C00.toInt(), updated.shapeColorArgb)
    }

    private fun labelLayerWithGradient(): EditorLayer {
        val gradient = EditorGradientMapper.buildLinearGradient(
            color1Argb = 0xFFFF8C00.toInt(),
            color2Argb = 0xFFFFF8E1.toInt(),
            angleDegrees = 90f,
        )
        return EditorLayer(
            id = "layer-1",
            type = LayerType.SHAPE_TEXT,
            shapeType = ShapeType.CARD,
            shapeColorArgb = 0xFFFF8C00.toInt(),
            fillGradient = gradient,
            appearance = EditorAppearance(alpha = 1f),
        )
    }

    private fun shapeDelegateFixture(initialLayer: EditorLayer = labelLayerWithGradient()): DelegateFixture {
        var state = EditorState(
            layers = listOf(initialLayer),
            selectedLayerId = initialLayer.id,
        )
        val delegate = ShapeViewModelDelegate(
            layerFactory = EditorLayerFactory(),
            shapeFitFlow = MutableSharedFlow(extraBufferCapacity = 1),
            readState = { state },
            updateState = { reducer -> state = state.reducer() },
            requestHistoryPush = {},
            pushHistory = {},
        )
        return DelegateFixture(
            delegate = delegate,
            readLayer = { state.layers.first { it.id == initialLayer.id } },
        )
    }

    private data class DelegateFixture(
        val delegate: ShapeViewModelDelegate,
        val readLayer: () -> EditorLayer,
    )
}
