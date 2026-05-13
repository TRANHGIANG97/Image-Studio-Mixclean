package com.thgiang.image.core.util.processors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.processors.ProcessorUtils.logOom

object DarkenProcessor {
    private const val TAG = "DarkenProcessor"

    suspend fun applyDarken(
        context: Context,
        bitmap: Bitmap,
        intensity: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        val clamped = intensity.coerceIn(0f, 1f)
        val darkenAlpha = 1.0f - clamped
        PortraitProcessor.applyPortrait(
            context = context,
            bitmap = bitmap,
            blurRadius = 0f,
            darkenAlpha = darkenAlpha,
            vignette = vignette,
            backgroundRemoverRepository = backgroundRemoverRepository
        )
    }.onFailure { e -> logOom(TAG, e) }.getOrNull()

    suspend fun applyDarkenCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        intensity: Float,
        vignette: Boolean
    ): Bitmap? = runCatching {
        val clamped = intensity.coerceIn(0f, 1f)
        val darkenAlpha = 1.0f - clamped
        PortraitProcessor.applyPortraitCached(
            bitmap = bitmap,
            foreground = foreground,
            blurRadius = 0f,
            darkenAlpha = darkenAlpha,
            vignette = vignette
        )
    }.onFailure { e -> logOom(TAG, e) }.getOrNull()

    fun applyDarkenVignetteToBitmap(
        base: Bitmap,
        darkenAlpha: Float,
        vignette: Boolean
    ): Bitmap {
        val a = darkenAlpha.coerceIn(0f, 1f)
        if (a <= 0f) return base

        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (!vignette) {
            paint.color = Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        } else {
            val w = out.width.toFloat()
            val h = out.height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = (minOf(w, h) * 0.72f).coerceAtLeast(1f)
            val shader = android.graphics.RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                    Color.argb(0, 0, 0, 0),
                    Color.argb((a * 0.65f * 255).toInt().coerceIn(0, 255), 0, 0, 0),
                    Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        return out
    }
}
