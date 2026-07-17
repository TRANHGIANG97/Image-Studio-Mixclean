package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.mapper.ProductExportLayout
import com.thgiang.image.studio.ui.editor.model.CropRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductExportLayoutTest {

    @Test
    fun `fit centers letterboxed image when slot is taller than bitmap`() {
        // Slot 200x400, image 200x100 → Fit scale 1, letterbox 150px top/bottom.
        val layout = ProductExportLayout.compute(
            templateWidth = 1000,
            templateHeight = 1000,
            shapeWidthPx = 200f,
            shapeHeightPx = 400f,
            viewportScale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            cropRatio = CropRatio.ORIGINAL,
            imageWidth = 200,
            imageHeight = 100,
        )

        assertEquals(200f, layout.drawW, 0.01f)
        assertEquals(400f, layout.drawH, 0.01f)
        assertEquals(1f, layout.fitScale, 0.01f)
        assertEquals(layout.drawX, layout.fittedX, 0.01f)
        assertEquals(layout.drawY + 150f, layout.fittedY, 0.01f)
    }

    @Test
    fun `matching aspects produce identical fit and fill`() {
        val layout = ProductExportLayout.compute(
            templateWidth = 800,
            templateHeight = 800,
            shapeWidthPx = 400f,
            shapeHeightPx = 200f,
            viewportScale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            cropRatio = CropRatio.ORIGINAL,
            imageWidth = 800,
            imageHeight = 400,
        )
        assertEquals(0.5f, layout.fitScale, 0.01f)
        assertEquals(layout.drawX, layout.fittedX, 0.01f)
        assertEquals(layout.drawY, layout.fittedY, 0.01f)
        assertEquals(400f, layout.drawW, 0.01f)
        assertEquals(200f, layout.drawH, 0.01f)
    }

    @Test
    fun `offset shifts draw box and fitted bitmap together`() {
        val layout = ProductExportLayout.compute(
            templateWidth = 1000,
            templateHeight = 1000,
            shapeWidthPx = 100f,
            shapeHeightPx = 100f,
            viewportScale = 2f,
            offsetX = 50f,
            offsetY = -30f,
            cropRatio = CropRatio.ORIGINAL,
            imageWidth = 100,
            imageHeight = 100,
        )
        assertEquals(450f, layout.drawX, 0.01f) // (1000-200)/2 + 50
        assertEquals(370f, layout.drawY, 0.01f) // (1000-200)/2 - 30
        assertEquals(layout.drawX, layout.fittedX, 0.01f)
        assertEquals(layout.drawY, layout.fittedY, 0.01f)
        assertEquals(2f, layout.fitScale, 0.01f)
    }

    @Test
    fun `wide image in square slot letterboxes vertically`() {
        val layout = ProductExportLayout.compute(
            templateWidth = 500,
            templateHeight = 500,
            shapeWidthPx = 200f,
            shapeHeightPx = 200f,
            viewportScale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            cropRatio = CropRatio.ORIGINAL,
            imageWidth = 400,
            imageHeight = 100,
        )
        assertEquals(0.5f, layout.fitScale, 0.01f)
        assertEquals(layout.drawX, layout.fittedX, 0.01f)
        assertTrue(layout.fittedY > layout.drawY)
        assertEquals(layout.drawY + 75f, layout.fittedY, 0.01f)
    }
}
