package com.thgiang.image.studio.ui.editor.canvas.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditTapMapperTest {

    @Test
    fun layoutOrigin_wrapContent_isCentered() {
        val origin = inlineTextLayoutOriginX(
            contentCenterX = 200f,
            screenW = 200f,
            layoutWidth = 80f,
            paddingXPx = 8f,
        )
        // Centered: 200 - 80/2 = 160
        assertEquals(160f, origin, 0.01f)
    }

    @Test
    fun layoutOrigin_fullWidth_usesFrameLeftPlusPadding() {
        val origin = inlineTextLayoutOriginX(
            contentCenterX = 200f,
            screenW = 200f,
            layoutWidth = 184f, // ~ screenW - 2*padding
            paddingXPx = 8f,
        )
        // frameLeft (100) + padding 8 = 108
        assertEquals(108f, origin, 0.01f)
    }

    @Test
    fun textSelectionRange_ordersAscending() {
        assertEquals(3 to 10, textSelectionRange(10, 3))
        assertEquals(0 to 0, textSelectionRange(-2, -5))
    }

    @Test
    fun layoutOrigin_matchesGeometricContentCenter() {
        val contentCenterX = 150f
        val layoutWidth = 80f
        val origin = inlineTextLayoutOriginX(
            contentCenterX = contentCenterX,
            screenW = 120f,
            layoutWidth = layoutWidth,
            paddingXPx = 0f,
        )
        assertEquals(contentCenterX - layoutWidth / 2f, origin, 0.01f)
    }
}
