package com.thgiang.image.studio.ui.editor

import com.thgiang.image.core.util.processors.OpaqueContentBounds
import com.thgiang.image.studio.ui.editor.model.CropRatio
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorProduct
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.imageLayerTightMetrics
import com.thgiang.image.studio.ui.editor.model.layerContentSize
import com.thgiang.image.studio.ui.editor.model.withOpaqueContentBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageLayerTightMetricsTest {

    @Test
    fun `imageLayerTightMetrics returns null when no opaque inset`() {
        val layer = stickerLayer()
        assertNull(layer.imageLayerTightMetrics())
    }

    @Test
    fun `imageLayerTightMetrics shrinks bbox for centered opaque content`() {
        val layer = stickerLayer(
            bitmapW = 200,
            bitmapH = 200,
            shapeW = 200f,
            shapeH = 200f,
        ).withOpaqueContentBounds(
            OpaqueContentBounds(left = 50, top = 60, width = 100, height = 80),
        )

        val metrics = layer.imageLayerTightMetrics()!!

        assertEquals(100f, metrics.contentWidth, 0.01f)
        assertEquals(80f, metrics.contentHeight, 0.01f)
        assertEquals(0f, metrics.centerOffsetX, 0.01f)
        assertEquals(0f, metrics.centerOffsetY, 0.01f)
    }

    @Test
    fun `imageLayerTightMetrics offsets center for asymmetric padding`() {
        val layer = stickerLayer(
            bitmapW = 200,
            bitmapH = 200,
            shapeW = 200f,
            shapeH = 200f,
        ).withOpaqueContentBounds(
            OpaqueContentBounds(left = 20, top = 40, width = 80, height = 60),
        )

        val metrics = layer.imageLayerTightMetrics()!!

        assertEquals(80f, metrics.contentWidth, 0.01f)
        assertEquals(60f, metrics.contentHeight, 0.01f)
        assertEquals(-40f, metrics.centerOffsetX, 0.01f)
        assertEquals(-30f, metrics.centerOffsetY, 0.01f)
    }

    @Test
    fun `layerContentSize uses tight metrics for image layers`() {
        val layer = stickerLayer().withOpaqueContentBounds(
            OpaqueContentBounds(left = 0, top = 0, width = 120, height = 90),
        )

        val (w, h) = layerContentSize(layer)

        assertEquals(120f, w, 0.01f)
        assertEquals(90f, h, 0.01f)
    }

    @Test
    fun `withOpaqueContentBounds clears inset when bounds are null`() {
        val layer = stickerLayer().withOpaqueContentBounds(
            OpaqueContentBounds(left = 10, top = 20, width = 80, height = 60),
        )

        val cleared = layer.withOpaqueContentBounds(null)

        assertEquals(0, cleared.opaqueContentLeftPx)
        assertEquals(0, cleared.opaqueContentTopPx)
        assertEquals(0, cleared.opaqueContentWidthPx)
        assertEquals(0, cleared.opaqueContentHeightPx)
        assertNull(cleared.imageLayerTightMetrics())
    }

    private fun stickerLayer(
        bitmapW: Int = 512,
        bitmapH: Int = 512,
        shapeW: Float = 512f,
        shapeH: Float = 512f,
    ): EditorLayer = EditorLayer(
        type = LayerType.IMAGE,
        product = EditorProduct(
            foregroundUriString = "file:///sticker.png",
            baseWidth = bitmapW,
            baseHeight = bitmapH,
        ),
        shapeWidthPx = shapeW,
        shapeHeightPx = shapeH,
        cropRatio = CropRatio.ORIGINAL,
        viewport = EditorViewport(scale = 0.5f),
    )
}
