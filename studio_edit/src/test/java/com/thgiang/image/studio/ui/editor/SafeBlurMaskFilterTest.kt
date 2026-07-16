package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.mapper.SafeBlurMaskFilter
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.resolvedShadowBlurRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeBlurMaskFilterTest {

    @Test
    fun sanitizeBlurRadius_zeroOrNegative_returnsNull() {
        assertNull(SafeBlurMaskFilter.sanitizeBlurRadius(0f))
        assertNull(SafeBlurMaskFilter.sanitizeBlurRadius(-1f))
    }

    @Test
    fun sanitizeBlurRadius_nonFinite_returnsNull() {
        assertNull(SafeBlurMaskFilter.sanitizeBlurRadius(Float.NaN))
        assertNull(SafeBlurMaskFilter.sanitizeBlurRadius(Float.POSITIVE_INFINITY))
    }

    @Test
    fun sanitizeBlurRadius_positive_coercesToMinimum() {
        assertEquals(0.1f, SafeBlurMaskFilter.sanitizeBlurRadius(0.05f)!!, 0.001f)
        assertEquals(12f, SafeBlurMaskFilter.sanitizeBlurRadius(12f)!!, 0.001f)
    }

    @Test
    fun adminShadowBlurZero_resolvesToZeroAndSkipsBlur() {
        val appearance = EditorAppearance(
            shadowIntensity = 0.5f,
            shadowBlur = 0f,
        )
        assertEquals(0f, appearance.resolvedShadowBlurRadius(), 0f)
        assertNull(SafeBlurMaskFilter.sanitizeBlurRadius(appearance.resolvedShadowBlurRadius()))
    }
}
