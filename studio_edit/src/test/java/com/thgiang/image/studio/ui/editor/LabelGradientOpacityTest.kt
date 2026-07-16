package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.label.factory.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.shape.ShapeViewModelDelegate
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
    fun `updateShapeColor preserves layout dimensions on text-only layer`() {
        val layer = EditorLayer(
            id = "layer-text-only",
            type = LayerType.SHAPE_TEXT,
            shapeType = ShapeType.TEXT_ONLY,
            shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
            shapeWidthPx = 420f,
            shapeHeightPx = 180f,
            textSizeSp = 65f,
            text = "dfuuiiii yd dssd hgf",
        )
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeColor(0xFFFCE4EC.toInt())

        val updated = fixture.readLayer()
        assertEquals(ShapeType.TEXT_ONLY, updated.shapeType)
        assertEquals(420f, updated.shapeWidthPx, 0.01f)
        assertEquals(180f, updated.shapeHeightPx, 0.01f)
        assertEquals(65f, updated.textSizeSp, 0.01f)
        assertEquals(0xFFFCE4EC.toInt(), updated.shapeColorArgb)
    }

    @Test
    fun `clear shape color preserves layout dimensions on text-only layer`() {
        val layer = EditorLayer(
            id = "layer-text-only",
            type = LayerType.SHAPE_TEXT,
            shapeType = ShapeType.TEXT_ONLY,
            shapeColorArgb = 0xFFFCE4EC.toInt(),
            shapeWidthPx = 420f,
            shapeHeightPx = 180f,
            textSizeSp = 65f,
            text = "dfuuiiii yd dssd hgf",
        )
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeColor(ShapeLabelDefaults.TRANSPARENT_FILL_ARGB)

        val updated = fixture.readLayer()
        assertEquals(ShapeType.TEXT_ONLY, updated.shapeType)
        assertEquals(420f, updated.shapeWidthPx, 0.01f)
        assertEquals(180f, updated.shapeHeightPx, 0.01f)
    }

    @Test
    fun `stroke preset preserves layout dimensions on text-only layer`() {
        val layer = textOnlyLayer()
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeColor(0xFFE3F2FD.toInt())
        fixture.delegate.updateStrokeColor(0xFF1565C0.toInt())
        fixture.delegate.updateStrokeWidth(2f)

        val updated = fixture.readLayer()
        assertEquals(ShapeType.TEXT_ONLY, updated.shapeType)
        assertEquals(420f, updated.shapeWidthPx, 0.01f)
        assertEquals(180f, updated.shapeHeightPx, 0.01f)
    }

    @Test
    fun `clear preset preserves layout dimensions on text-only layer`() {
        val layer = textOnlyLayer().copy(
            shapeColorArgb = 0xFFE3F2FD.toInt(),
            strokeColorArgb = 0xFF1565C0.toInt(),
            strokeWidthPx = 2f,
            appearance = EditorAppearance(shadowIntensity = 0.15f),
        )
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeColor(ShapeLabelDefaults.TRANSPARENT_FILL_ARGB)
        fixture.delegate.updateStrokeColor(0x00000000)
        fixture.delegate.updateStrokeWidth(0f)

        val updated = fixture.readLayer()
        assertEquals(420f, updated.shapeWidthPx, 0.01f)
        assertEquals(180f, updated.shapeHeightPx, 0.01f)
    }

    private fun textOnlyLayer(): EditorLayer = EditorLayer(
        id = "layer-text-only",
        type = LayerType.SHAPE_TEXT,
        shapeType = ShapeType.TEXT_ONLY,
        shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
        shapeWidthPx = 420f,
        shapeHeightPx = 180f,
        textSizeSp = 65f,
        text = "dfuuiiii yd dssd hgf",
    )

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

    @Test
    fun `updateShapeFillOpacity only changes shape alpha not appearance alpha`() {
        val layer = labelLayerWithGradient().copy(
            textColorArgb = 0xFF000000.toInt(),
            appearance = EditorAppearance(alpha = 1f),
        )
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeFillOpacity(0.24f)

        val updated = fixture.readLayer()
        assertEquals(0x3DFF8C00.toInt(), updated.shapeColorArgb)
        assertEquals(1f, updated.appearance.alpha, 0.001f)
        assertEquals(0xFF000000.toInt(), updated.textColorArgb)
        assertNotNull(updated.fillGradient)
    }

    @Test
    fun `updateShapeFillOpacity preserves fillGradient`() {
        val layer = labelLayerWithGradient()
        val originalGradient = layer.fillGradient
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeFillOpacity(0.29f)

        val updated = fixture.readLayer()
        assertNotNull(updated.fillGradient)
        assertEquals(originalGradient, updated.fillGradient)
        assertEquals(0x49FF8C00.toInt(), updated.shapeColorArgb)
    }

    @Test
    fun `clear shape color preserves text appearance alpha`() {
        val layer = labelLayerWithGradient().copy(
            appearance = EditorAppearance(alpha = 0.5f),
        )
        val fixture = shapeDelegateFixture(initialLayer = layer)
        fixture.delegate.updateShapeColor(ShapeLabelDefaults.TRANSPARENT_FILL_ARGB)

        val updated = fixture.readLayer()
        assertEquals(ShapeLabelDefaults.TRANSPARENT_FILL_ARGB, updated.shapeColorArgb)
        assertEquals(0.5f, updated.appearance.alpha, 0.001f)
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
