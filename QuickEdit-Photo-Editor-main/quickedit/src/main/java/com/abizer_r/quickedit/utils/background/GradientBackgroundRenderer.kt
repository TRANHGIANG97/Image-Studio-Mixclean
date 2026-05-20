package com.abizer_r.quickedit.utils.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.abizer_r.quickedit.ui.backgroundMode.BackgroundGradientPreset
import com.abizer_r.quickedit.ui.backgroundMode.GradientDirection

object GradientBackgroundRenderer {

    fun createGradientBitmap(
        width: Int,
        height: Int,
        preset: BackgroundGradientPreset
    ): Bitmap {
        require(width > 0 && height > 0) { "Bitmap size must be positive." }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val shader = when (preset.direction) {
            GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                preset.colors.toIntArray(),
                null,
                Shader.TileMode.CLAMP
            )
            GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> LinearGradient(
                0f,
                height.toFloat(),
                width.toFloat(),
                0f,
                preset.colors.toIntArray(),
                null,
                Shader.TileMode.CLAMP
            )
        }

        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}
