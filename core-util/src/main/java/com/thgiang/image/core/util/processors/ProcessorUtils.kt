package com.thgiang.image.core.util.processors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.thgiang.image.core.util.MemoryUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProcessorUtils {
    private const val TAG = "ProcessorUtils"

    fun Bitmap.toArgbBitmap(): Bitmap? {
        return copy(Bitmap.Config.ARGB_8888, true)
    }

    suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val maxSide = MemoryUtil.maxDecodeSide(context)
                val opts = BitmapFactory.Options().apply {
                    inMutable = false
                    if (maxSide > 0) {
                        inJustDecodeBounds = true
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, this)
                        }
                        inSampleSize = MemoryUtil.calculateInSampleSize(this, maxSide)
                        inJustDecodeBounds = false
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }?.toArgbBitmap()
            }.onFailure { e ->
                if (e is OutOfMemoryError) Log.e(TAG, "OOM decoding bitmap from URI", e)
            }.getOrNull()
        }

    fun createColorBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }

    fun createGradientBitmap(width: Int, height: Int, colors: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            colors, null, android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    fun downscaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val maxDim = maxOf(w, h)
        if (maxDim <= maxDimension) return source
        val scale = maxDimension / maxDim
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    fun compositeForegroundOverBackground(background: Bitmap, foreground: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        canvas.drawBitmap(background, 0f, 0f, paint)
        
        if (foreground.width != background.width || foreground.height != background.height) {
            val scaleX = background.width.toFloat() / foreground.width
            val scaleY = background.height.toFloat() / foreground.height
            val scale = minOf(scaleX, scaleY)
            val dx = (background.width - foreground.width * scale) / 2
            val dy = (background.height - foreground.height * scale) / 2
            
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            canvas.drawBitmap(foreground, 0f, 0f, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(foreground, 0f, 0f, paint)
        }
        
        return result
    }

    fun logOom(tag: String, e: Throwable) {
        if (e is OutOfMemoryError) {
            Log.e(tag, "OutOfMemoryError in image processing", e)
        }
    }
}
