package com.thgiang.image.studio.ui.editor.mapper

import android.graphics.BlurMaskFilter
import android.graphics.Paint

/**
 * BlurMaskFilter throws [IllegalArgumentException] when radius <= 0.
 * Templates from admin_web may set shadowBlur = 0 (sharp shadow, no blur).
 */
object SafeBlurMaskFilter {
    private const val MIN_RADIUS = 0.1f
    private const val MAX_RADIUS = 256f

    fun sanitizeBlurRadius(radius: Float): Float? {
        if (!radius.isFinite() || radius <= 0f) return null
        return radius.coerceIn(MIN_RADIUS, MAX_RADIUS)
    }

    fun createOrNull(
        radius: Float,
        blur: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL,
    ): BlurMaskFilter? {
        val safeRadius = sanitizeBlurRadius(radius) ?: return null
        return runCatching { BlurMaskFilter(safeRadius, blur) }.getOrNull()
    }
}

fun Paint.setSafeBlurMaskFilter(
    radius: Float,
    blur: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL,
) {
    SafeBlurMaskFilter.createOrNull(radius, blur)?.let { maskFilter = it }
}
