package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.label.model.ShapeTextBoundsResolver
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.ShapeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeTextBoundsResolverTest {

    @Test
    fun textLayoutIncludePad_isFalse_forBoundsDrawParity() {
        // Bounds resolver and EditorTextRenderMapper.drawFlatTextOnCanvas must share this flag.
        assertFalse(ShapeTextBoundsResolver.TEXT_LAYOUT_INCLUDE_PAD)
    }

    @Test
    fun shadowBleedPad_noShadow_returnsZero() {
        val layer = EditorLayer(
            appearance = EditorAppearance(shadowIntensity = 0f),
            viewport = com.thgiang.image.studio.ui.editor.model.EditorViewport(scale = 2f),
        )

        assertEquals(0f, ShapeTextBoundsResolver.shadowBleedPad(layer), 0.001f)
    }

    @Test
    fun shadowBleedPad_lowIntensity_returnsZero() {
        val layer = EditorLayer(
            appearance = EditorAppearance(shadowIntensity = 0.05f),
            viewport = com.thgiang.image.studio.ui.editor.model.EditorViewport(scale = 1f),
        )

        assertEquals(0f, ShapeTextBoundsResolver.shadowBleedPad(layer), 0.001f)
    }

    @Test
    fun boundsDecorPadding_textOnly_ignoresStrokeAndShadow() {
        val layer = EditorLayer(
            shapeType = ShapeType.TEXT_ONLY,
            strokeWidthPx = 4f,
            strokeColorArgb = 0xFF1565C0.toInt(),
            appearance = EditorAppearance(
                shadowIntensity = 0.5f,
                shadowDistance = 10f,
                shadowBlur = 10f,
            ),
        )

        val (strokePad, bleedPad) = ShapeTextBoundsResolver.boundsDecorPadding(layer)
        assertEquals(0f, strokePad, 0.001f)
        assertEquals(0f, bleedPad, 0.001f)
    }

    @Test
    fun boundsDecorPadding_card_includesStrokeAndShadow() {
        val layer = EditorLayer(
            shapeType = ShapeType.CARD,
            strokeWidthPx = 4f,
            strokeColorArgb = 0xFF1565C0.toInt(),
            appearance = EditorAppearance(
                shadowIntensity = 0.5f,
                shadowDistance = 10f,
                shadowBlur = 10f,
            ),
        )

        val (strokePad, bleedPad) = ShapeTextBoundsResolver.boundsDecorPadding(layer)
        assertTrue(strokePad > 0f)
        assertTrue(bleedPad > 0f)
    }

    @Test
    fun shadowBleedPad_withShadow_returnsPositiveBleed() {
        val layer = EditorLayer(
            appearance = EditorAppearance(
                shadowAngle = 45f,
                shadowDistance = 10f,
                shadowIntensity = 0.5f,
                shadowBlur = 10f,
            ),
            viewport = com.thgiang.image.studio.ui.editor.model.EditorViewport(scale = 2f),
        )

        val bleed = ShapeTextBoundsResolver.shadowBleedPad(layer)
        assertTrue(bleed > 0f)
    }
}
