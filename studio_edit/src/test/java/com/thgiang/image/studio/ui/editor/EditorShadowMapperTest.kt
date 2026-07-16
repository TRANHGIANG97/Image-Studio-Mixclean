package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.mapper.EditorShadowMapper
import com.thgiang.image.studio.ui.editor.mapper.SafeBlurMaskFilter
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.opaqueShadowColorArgb
import com.thgiang.image.studio.ui.editor.model.resolvedShadowBlurRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorShadowMapperTest {

    @Test
    fun testShadowOffsetLocalPx_noRotation() {
        val appearance = EditorAppearance(
            shadowAngle = 45f,
            shadowDistance = 10f,
            shadowIntensity = 0.3f
        )
        // At 0 rotation, local offset should equal world offset
        val (dxW, dyW) = EditorShadowMapper.shadowOffsetWorldPx(appearance, scale = 1f)
        val (dxL, dyL) = EditorShadowMapper.shadowOffsetLocalPx(appearance, scale = 1f, rotationDeg = 0f)

        assertEquals(dxW, dxL, 0.001f)
        assertEquals(dyW, dyL, 0.001f)
    }

    @Test
    fun testShadowOffsetLocalPx_rotation90() {
        val appearance = EditorAppearance(
            shadowAngle = 0f, // Points directly right (10, 0)
            shadowDistance = 10f,
            shadowIntensity = 0.3f
        )
        // Rotated 90 degrees clockwise.
        // In local space, to point right in world space:
        // dx_w = 10, dy_w = 0
        // θ = 90 deg -> cos(-90) = 0, sin(-90) = -1
        // dx_l = dx_w * cos(-90) - dy_w * sin(-90) = 0
        // dy_l = dx_w * sin(-90) + dy_w * cos(-90) = -10
        val (dxL, dyL) = EditorShadowMapper.shadowOffsetLocalPx(appearance, scale = 1f, rotationDeg = 90f)

        assertEquals(0f, dxL, 0.001f)
        assertEquals(-10f, dyL, 0.001f)
    }

    @Test
    fun testComputeShadowBleedPx_noShadow() {
        val appearance = EditorAppearance(
            shadowIntensity = 0f // No shadow
        )
        val bleed = EditorShadowMapper.computeShadowBleedPx(
            appearance = appearance,
            scale = 1f,
            rotationDeg = 0f,
            extraStrokePx = 5f
        )
        assertEquals(5f, bleed, 0.001f)
    }

    @Test
    fun testComputeShadowBleedPx_withShadow() {
        val appearance = EditorAppearance(
            shadowAngle = 45f,
            shadowDistance = 10f,
            shadowIntensity = 0.5f, // blur radius resolves to 18f - 0.5 * 12f = 12f
            shadowBlur = 10f // Override to 10f
        )
        val bleed = EditorShadowMapper.computeShadowBleedPx(
            appearance = appearance,
            scale = 2f, // scaled values: distance = 20, blur = 20
            rotationDeg = 0f,
            extraStrokePx = 0f
        )
        // distance: dx = 20*cos(45) ~ 14.14, dy = 14.14
        // kernel = 3 * blur = 60
        // extentX = 14.14 + 60 = 74.14
        // extentY = 14.14 + 60 = 74.14
        // bleed = extentX + 2f = 76.14
        assertTrue(bleed > 70f)
        assertTrue(bleed < 80f)
    }

    @Test
    fun configureDropShadowPaint_zeroBlur_doesNotSetMaskFilter() {
        val appearance = EditorAppearance(
            shadowIntensity = 0.5f,
            shadowBlur = 0f,
        )
        assertNull(SafeBlurMaskFilter.sanitizeBlurRadius(appearance.resolvedShadowBlurRadius()))
        val paint = EditorShadowMapper.configureDropShadowPaint(appearance)
        assertNull(paint.maskFilter)
    }

    @Test
    fun opaqueShadowColorArgb_stripsEmbeddedAlpha() {
        val appearance = EditorAppearance(
            shadowIntensity = 0.4f,
            shadowColorArgb = 0x66000000.toInt(), // semi-transparent black from admin rgba
        )
        assertEquals(0xFF000000.toInt(), appearance.opaqueShadowColorArgb())
        val paint = EditorShadowMapper.configureDropShadowPaint(appearance)
        // Paint.color alpha is overwritten by setAlpha, but RGB must stay black.
        assertEquals(0, android.graphics.Color.red(paint.color))
        assertEquals(0, android.graphics.Color.green(paint.color))
        assertEquals(0, android.graphics.Color.blue(paint.color))
    }
}
