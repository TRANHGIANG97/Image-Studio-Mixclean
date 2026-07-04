package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.LayerType.IMAGE
import com.thgiang.image.studio.ui.editor.model.LayerType.SHAPE_TEXT
import com.thgiang.image.studio.ui.editor.canvas.LayerHitTest
import com.thgiang.image.studio.ui.editor.canvas.LayerHitTestContext
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.EditorProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerHitTestTest {

    @Test
    fun `returns top-most layer first`() {
        val bottom = imageLayer(
            id = "bottom",
            offsetX = 0f,
            offsetY = 0f,
            baseWidth = 400,
            baseHeight = 400,
        )
        val top = imageLayer(
            id = "top",
            offsetX = 0f,
            offsetY = 0f,
            baseWidth = 200,
            baseHeight = 200,
        )
        val layers = listOf(bottom, top)
        val context = hitContext(
            tapX = 500f,
            tapY = 500f,
            displayWidthPx = 1000f,
            displayHeightPx = 1000f,
            templateLeftPx = 0f,
            templateTopPx = 0f,
            calculatedScale = 1f,
        )

        val hits = LayerHitTest.hitLayersAtPoint(layers, context)

        assertEquals(listOf("top", "bottom"), hits.map { it.id })
    }

    @Test
    fun `returns empty when tap misses all layers`() {
        val layer = imageLayer(
            id = "far",
            offsetX = 400f,
            offsetY = 400f,
            baseWidth = 50,
            baseHeight = 50,
        )
        val context = hitContext(
            tapX = 10f,
            tapY = 10f,
            displayWidthPx = 1000f,
            displayHeightPx = 1000f,
            templateLeftPx = 0f,
            templateTopPx = 0f,
            calculatedScale = 1f,
        )

        val hits = LayerHitTest.hitLayersAtPoint(listOf(layer), context)

        assertTrue(hits.isEmpty())
    }

    @Test
    fun `shape text layer is hittable`() {
        val textLayer = EditorLayer(
            id = "text",
            type = SHAPE_TEXT,
            shapeWidthPx = 200f,
            shapeHeightPx = 80f,
            viewport = EditorViewport(offsetX = 0f, offsetY = 0f, scale = 1f),
        )
        val context = hitContext(
            tapX = 500f,
            tapY = 500f,
            displayWidthPx = 1000f,
            displayHeightPx = 1000f,
            templateLeftPx = 0f,
            templateTopPx = 0f,
            calculatedScale = 1f,
        )

        val hits = LayerHitTest.hitLayersAtPoint(listOf(textLayer), context)

        assertEquals(listOf("text"), hits.map { it.id })
    }

    private fun imageLayer(
        id: String,
        offsetX: Float,
        offsetY: Float,
        baseWidth: Int,
        baseHeight: Int,
    ) = EditorLayer(
        id = id,
        type = IMAGE,
        product = EditorProduct(
            originalUriString = "https://example.com/$id.png",
            foregroundUriString = "https://example.com/$id.png",
            isBackgroundRemoved = true,
            baseWidth = baseWidth,
            baseHeight = baseHeight,
        ),
        viewport = EditorViewport(offsetX = offsetX, offsetY = offsetY, scale = 1f),
    )

    private fun hitContext(
        tapX: Float,
        tapY: Float,
        displayWidthPx: Float,
        displayHeightPx: Float,
        templateLeftPx: Float,
        templateTopPx: Float,
        calculatedScale: Float,
        selectionHitPaddingPx: Float = 0f,
    ) = LayerHitTestContext(
        tapX = tapX,
        tapY = tapY,
        displayWidthPx = displayWidthPx,
        displayHeightPx = displayHeightPx,
        templateLeftPx = templateLeftPx,
        templateTopPx = templateTopPx,
        calculatedScale = calculatedScale,
        selectionHitPaddingPx = selectionHitPaddingPx,
    )
}
