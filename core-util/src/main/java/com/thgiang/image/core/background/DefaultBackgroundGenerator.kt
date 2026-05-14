// image/core/background/DefaultBackgroundGenerator.kt
package com.thgiang.image.core.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import com.thgiang.image.core.model.PresetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultBackgroundGenerator : BackgroundGenerator {

    override suspend fun generatePreset(style: PresetStyle, width: Int, height: Int): Bitmap =
        withContext(Dispatchers.Default) {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            when (style) {
                PresetStyle.NOIR -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF0D1019.toInt(), 0xFF1A1C24.toInt(), 0xFF111317.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawDiagonalLines(canvas, width, height, 0x15FFFFFF.toInt(), 1.2f)
                }
                PresetStyle.CLEAN -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFFFCFCFD.toInt(), 0xFFF1F3F7.toInt(), 0xFFE9EDF3.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawHorizontalLines(canvas, width, height, 0x389EA8B8.toInt(), 1f)
                }
                PresetStyle.AURORA -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF0B1020.toInt(), 0xFF1A1C3A.toInt(), 0xFF1E3154.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawRadialGlow(canvas, width, height, 0.2f, 0.15f, 0.6f, 0x8C5AEEFF.toInt())
                    drawRadialGlow(canvas, width, height, 0.85f, 0.55f, 0.65f, 0x808A7BFF.toInt())
                }
                PresetStyle.DUOTONE -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF2B1F3E.toInt(), 0xFF5F4B8B.toInt(), 0xFF00B5D8.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawRadialGlow(canvas, width, height, 0.15f, 0.18f, 0.45f, 0x8CFFB870.toInt())
                    drawRadialGlow(canvas, width, height, 0.9f, 0.45f, 0.55f, 0x7363F0FF.toInt())
                }
                PresetStyle.NEON_GRID -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF040912.toInt(), 0xFF0A1325.toInt(), 0xFF111F37.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawGrid(canvas, width, height, 0x4D3ED8FF.toInt(), 1.5f)
                }
                PresetStyle.LIQUID_GLASS -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFFF6F4FF.toInt(), 0xFFEAF2FF.toInt(), 0xFFF7F9FF.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawSoftBubble(canvas, width, height, 0.35f, 0.15f, 0.35f, 0xD9FFFFFF.toInt(), 0x59DBE8FF.toInt())
                    drawSoftBubble(canvas, width, height, 0.15f, 0.45f, 0.25f, 0xB8B8DCFF.toInt(), 0x00FFFFFF.toInt())
                }
                PresetStyle.SUNSET_FILM -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF2A122A.toInt(), 0xFF8A3E6B.toInt(), 0xFFF09862.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    // sun
                    paint.shader = null
                    paint.color = 0xD9FFD087.toInt()
                    canvas.drawCircle(width * 0.22f, height * 0.12f, width * 0.15f, paint)
                    // bottom shadow
                    paint.shader = LinearGradient(0f, height * 0.5f, 0f, height.toFloat(),
                        intArrayOf(0x003B1D3F.toInt(), 0x733B1D3F.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, height * 0.5f, width.toFloat(), height.toFloat(), paint)
                }
                PresetStyle.CARBON_X -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF0A0A0D.toInt(), 0xFF181A20.toInt(), 0xFF101116.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawDiagonalLines(canvas, width, height, 0x0FFFFFFF.toInt(), 1.5f)
                    // accent bar
                    paint.shader = null
                    paint.color = 0x5244E8FF.toInt()
                    canvas.drawRect(width * 0.65f, 0f, width * 0.73f, height.toFloat(), paint)
                }
                PresetStyle.ROSE_GARDEN -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF5B2C8F.toInt(), 0xFFC0486C.toInt(), 0xFFF4A261.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    drawDiagonalLines(canvas, width, height, 0x1FFFFFFF.toInt(), 1.5f)
                }
                PresetStyle.PEACH_SKY -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt(), 0xFFF093FB.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                PresetStyle.GOLDEN_SUNSET -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFE66D.toInt(), 0xFF4ECDC4.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                PresetStyle.LAVENDER_DAWN -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFFA18CD1.toInt(), 0xFFFBC2EB.toInt(), 0xFFF6D365.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                PresetStyle.AQUA_BREEZE -> {
                    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt(), 0xFF43E97B.toInt()),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
            }
            result
        }

    private fun drawRadialGlow(canvas: Canvas, w: Int, h: Int, cxRatio: Float, cyRatio: Float, radiusRatio: Float, centerColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            w * cxRatio, h * cyRatio, w * radiusRatio,
            intArrayOf(centerColor, android.graphics.Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * cxRatio, h * cyRatio, w * radiusRatio, paint)
    }

    private fun drawGrid(canvas: Canvas, w: Int, h: Int, color: Int, strokeWidth: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.strokeWidth = strokeWidth
        }
        val stepX = w / 8f
        val stepY = h / 7f
        var x = stepX
        while (x < w) {
            canvas.drawLine(x, 0f, x, h.toFloat(), paint)
            x += stepX
        }
        var y = stepY
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, paint)
            y += stepY
        }
    }

    private fun drawDiagonalLines(canvas: Canvas, w: Int, h: Int, color: Int, strokeWidth: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.strokeWidth = strokeWidth
        }
        val step = w / 9f
        var dx = -h.toFloat()
        while (dx < w + h) {
            canvas.drawLine(dx, 0f, dx + h, h.toFloat(), paint)
            dx += step
        }
    }

    private fun drawHorizontalLines(canvas: Canvas, w: Int, h: Int, color: Int, strokeWidth: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.strokeWidth = strokeWidth
        }
        val step = h / 6f
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, paint)
            y += step
        }
    }

    private fun drawSoftBubble(canvas: Canvas, w: Int, h: Int, cxRatio: Float, cyRatio: Float, radiusRatio: Float, centerColor: Int, outerColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            w * cxRatio, h * cyRatio, w * radiusRatio,
            intArrayOf(centerColor, outerColor),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * cxRatio, h * cyRatio, w * radiusRatio, paint)
    }

    override fun generateColor(color: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }

    override fun generateGradient(colors: List<Int>, direction: GradientDirection, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val (x0, y0, x1, y1) = when (direction) {
            GradientDirection.TOP_TO_BOTTOM -> arrayOf(0f, 0f, 0f, height.toFloat())
            GradientDirection.LEFT_TO_RIGHT -> arrayOf(0f, 0f, width.toFloat(), 0f)
            GradientDirection.DIAGONAL -> arrayOf(0f, 0f, width.toFloat(), height.toFloat())
            GradientDirection.TOPLEFT_TO_BOTTOMRIGHT -> arrayOf(0f, 0f, width.toFloat(), height.toFloat())
        }
        paint.shader = LinearGradient(x0, y0, x1, y1, colors.toIntArray(), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    override suspend fun generateFromUri(uri: Uri, targetWidth: Int, targetHeight: Int, context: Context): Bitmap =
        withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: throw IllegalStateException("Cannot open image: $uri")

            val srcW = options.outWidth
            val srcH = options.outHeight
            val scaleFactor = maxOf(targetWidth / srcW.toFloat(), targetHeight / srcH.toFloat())
            val scaleW = (srcW * scaleFactor).toInt()
            val scaleH = (srcH * scaleFactor).toInt()

            val sampled = context.contentResolver.openInputStream(uri)?.use { inputStream2 ->
                BitmapFactory.decodeStream(inputStream2, null, BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(srcW, srcH, scaleW, scaleH)
                })
            } ?: throw IllegalStateException("Failed to decode bitmap from URI: $uri")

            val scaled = Bitmap.createScaledBitmap(sampled, targetWidth, targetHeight, true)
            sampled.recycle()
            scaled
        }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}