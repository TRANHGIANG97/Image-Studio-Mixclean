package com.thgiang.image.feature.home.ui.preset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Color
import com.thgiang.image.core.model.PresetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PresetBackgroundComposer {
    suspend fun compose(context: Context, foreground: Bitmap, style: PresetStyle): Bitmap? {
        return composePresetBackgroundBitmap(foreground, style)
    }
}

suspend fun composePresetBackgroundBitmap(
    foreground: Bitmap,
    style: PresetStyle
): Bitmap = withContext(Dispatchers.Default) {
    val width = foreground.width
    val height = foreground.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Simplified background drawing based on style
    when (style) {
        PresetStyle.NOIR -> canvas.drawColor(Color.BLACK)
        PresetStyle.CLEAN -> canvas.drawColor(Color.WHITE)
        PresetStyle.AURORA -> {
            val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 
                Color.parseColor("#FF00CC"), Color.parseColor("#333399"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        else -> canvas.drawColor(Color.LTGRAY)
    }
    
    paint.shader = null
    canvas.drawBitmap(foreground, 0f, 0f, paint)
    result
}
