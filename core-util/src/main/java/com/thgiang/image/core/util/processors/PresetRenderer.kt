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
                canvas.drawColor(Color.parseColor("#FF0D1019"))
                drawVignette(canvas, width, height, Color.parseColor("#FF000000"))
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
            // ── New bright presets ──────────────────────────────────────────────
            PresetStyle.ROSE_GARDEN -> {
                val shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FF5B2C8F"),  // deep purple top-left
                        Color.parseColor("#FFC0486C"),  // pink mid
                        Color.parseColor("#FFF4A261")   // warm orange bottom-right
                    ),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.PEACH_SKY -> {
                val shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FF667eea"),  // blue top-left
                        Color.parseColor("#FF764ba2"),  // purple mid
                        Color.parseColor("#FFF093FB")   // pink bottom-right
                    ),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.GOLDEN_SUNSET -> {
                val shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FFFF6B6B"),  // coral top-left
                        Color.parseColor("#FFFFE66D"),  // yellow mid
                        Color.parseColor("#FF4ECDC4")   // teal bottom-right
                    ),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.LAVENDER_DAWN -> {
                val shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FFa18cd1"),  // lavender top-left
                        Color.parseColor("#FFfbc2eb"),  // pink mid
                        Color.parseColor("#FFf6d365")   // peach bottom-right
                    ),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PresetStyle.AQUA_BREEZE -> {
                val shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FF4facfe"),  // bright blue top-left
                        Color.parseColor("#FF00f2fe"),  // cyan mid
                        Color.parseColor("#FF43e97b")   // mint green bottom-right
                    ),
                    null, Shader.TileMode.CLAMP)
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
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
