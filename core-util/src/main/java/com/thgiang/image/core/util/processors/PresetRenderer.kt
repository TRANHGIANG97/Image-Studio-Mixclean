package com.thgiang.image.core.util.processors

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import com.thgiang.image.core.model.PresetStyle

object PresetRenderer {

    fun createPresetBitmap(width: Int, height: Int, style: PresetStyle): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()

        when (style) {
            PresetStyle.NOIR -> drawNoir(canvas, paint, w, h)
            PresetStyle.CLEAN -> drawClean(canvas, paint, w, h)
            PresetStyle.AURORA -> drawAurora(canvas, paint, w, h)
            PresetStyle.DUOTONE -> drawDuotone(canvas, paint, w, h)
            PresetStyle.NEON_GRID -> drawNeonGrid(canvas, paint, w, h)
            PresetStyle.LIQUID_GLASS -> drawLiquidGlass(canvas, paint, w, h)
            PresetStyle.SUNSET_FILM -> drawSunsetFilm(canvas, paint, w, h)
            PresetStyle.CARBON_X -> drawCarbonX(canvas, paint, w, h)
        }
        return bitmap
    }

    private fun drawNoir(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#0D1019"), Color.parseColor("#1A1C24"), Color.parseColor("#111317")),
            null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 20
        val step = w / 6f
        for (i in -2..8) {
            val x = i * step
            canvas.drawLine(x, 0f, x - h, h, paint)
        }
    }

    private fun drawClean(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#FCFCFD"), Color.parseColor("#F1F3F7"), Color.parseColor("#E9EDF3")),
            null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = null
        paint.color = Color.parseColor("#9EA8B8")
        paint.alpha = 50
        for (i in 1..3) {
            val y = h * (i / 4f)
            canvas.drawLine(0f, y, w, y, paint)
        }
    }

    private fun drawAurora(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#1A1C3A"), Color.parseColor("#1E3154")),
            null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = android.graphics.RadialGradient(w * 0.25f, h * 0.2f, w * 0.6f,
            intArrayOf(Color.parseColor("#885AEEFF"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = android.graphics.RadialGradient(w * 0.8f, h * 0.6f, w * 0.7f,
            intArrayOf(Color.parseColor("#808A7BFF"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawDuotone(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#2B1F3E"), Color.parseColor("#5F4B8B"), Color.parseColor("#00B5D8")),
            null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = android.graphics.RadialGradient(w * 0.15f, h * 0.2f, w * 0.45f,
            intArrayOf(Color.parseColor("#8CFFB870"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = android.graphics.RadialGradient(w * 0.85f, h * 0.5f, w * 0.55f,
            intArrayOf(Color.parseColor("#7363F0FF"), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawNeonGrid(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        canvas.drawColor(Color.parseColor("#040912"))
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gridPaint.color = Color.parseColor("#3ED8FF")
        gridPaint.alpha = 75
        gridPaint.strokeWidth = 2f
        
        val stepX = w / 5f
        val stepY = h / 4f
        for (i in 1..4) {
            val x = i * stepX
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }
        for (i in 1..3) {
            val y = i * stepY
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
    }

    private fun drawLiquidGlass(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#F6F4FF"), Color.parseColor("#EAF2FF"), Color.parseColor("#F7F9FF")),
            null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 150
        canvas.drawCircle(w * 0.7f, h * 0.2f, w * 0.25f, paint)
        
        paint.color = Color.parseColor("#B8DCFF")
        paint.alpha = 100
        canvas.drawCircle(w * 0.2f, h * 0.4f, w * 0.2f, paint)
    }

    private fun drawSunsetFilm(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#2A122A"), Color.parseColor("#8A3E6B"), Color.parseColor("#F09862")),
            null, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = null
        paint.color = Color.parseColor("#FFFFD087")
        paint.alpha = 200
        canvas.drawCircle(w * 0.2f, h * 0.15f, w * 0.15f, paint)
    }

    private fun drawCarbonX(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        canvas.drawColor(Color.parseColor("#0A0A0D"))
        paint.color = Color.WHITE
        paint.alpha = 15
        val step = w / 7f
        for (i in -1..8) {
            val x = i * step
            canvas.drawLine(x, 0f, x + h * 0.7f, h, paint)
        }
        paint.color = Color.CYAN // aiAccent placeholder
        paint.alpha = 80
        canvas.drawRect(w * 0.55f, 0f, w * 0.63f, h, paint)
    }
}
