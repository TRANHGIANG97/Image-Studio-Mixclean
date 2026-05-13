package com.thgiang.image.core.util.processors

import android.graphics.*
import com.thgiang.image.core.model.PresetStyle

/**
 * Renders background preset bitmaps for when no image is selected or for preset selection.
 */
object PresetRenderer {

    fun createPresetBitmap(width: Int, height: Int, style: PresetStyle): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (style) {
            PresetStyle.NOIR -> {
                canvas.drawColor(Color.BLACK)
                drawVignette(canvas, width, height, Color.DKGRAY)
            }
            PresetStyle.CLEAN -> {
                canvas.drawColor(Color.WHITE)
                drawVignette(canvas, width, height, Color.LTGRAY)
            }
            PresetStyle.AURORA -> {
                val shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#FF4158D0"), Color.parseColor("#FFC850C0"), Color.parseColor("#FFFFCC70")),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.DUOTONE -> {
                val shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    intArrayOf(Color.parseColor("#FF1e3c72"), Color.parseColor("#FF2a5298")),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.NEON_GRID -> {
                canvas.drawColor(Color.parseColor("#FF000000"))
                paint.color = Color.parseColor("#3300FF00")
                paint.strokeWidth = 2f
                val spacing = 50f
                var x = 0f
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                    x += spacing
                }
                var y = 0f
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, paint)
                    y += spacing
                }
            }
            PresetStyle.LIQUID_GLASS -> {
                val shader = RadialGradient(width / 2f, height / 2f, Math.max(width, height).toFloat(),
                    intArrayOf(Color.parseColor("#FFa1c4fd"), Color.parseColor("#FFc2e9fb")),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.SUNSET_FILM -> {
                val shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    intArrayOf(Color.parseColor("#FFf093fb"), Color.parseColor("#FFf5576c")),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.CARBON_X -> {
                canvas.drawColor(Color.parseColor("#FF121212"))
                drawVignette(canvas, width, height, Color.BLACK)
            }
        }
        return bitmap
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, edgeColor: Int) {
        val shader = RadialGradient(width / 2f, height / 2f, Math.max(width, height).toFloat() * 0.7f,
            intArrayOf(Color.TRANSPARENT, edgeColor), null, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
