package com.thgiang.image.core.util.processors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.ExifInterface
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
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                // Apply EXIF orientation
                val rotation = getExifRotation(context, uri)
                (if (rotation != 0 && bitmap != null) {
                    rotateBitmap(bitmap, rotation)
                } else {
                    bitmap
                })?.toArgbBitmap()
            }.onFailure { e ->
                if (e is OutOfMemoryError) Log.e(TAG, "OOM decoding bitmap from URI", e)
            }.getOrNull()
        }

    /** Read EXIF orientation from URI, return clockwise rotation degrees. */
    private fun getExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
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
        return compositeForegroundOverBackground(background, foreground, 0f, 0f)
    }

    fun compositeForegroundOverBackground(
        background: Bitmap, foreground: Bitmap,
        offsetX: Float, offsetY: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(background, 0f, 0f, paint)

        if (foreground.width != background.width || foreground.height != background.height) {
            val scaleX = background.width.toFloat() / foreground.width
            val scaleY = background.height.toFloat() / foreground.height
            val scale = minOf(scaleX, scaleY)
            val drawW = foreground.width * scale
            val drawH = foreground.height * scale
            // Base centering + user offset
            val dx = (background.width - drawW) / 2 + offsetX
            val dy = (background.height - drawH) / 2 + offsetY

            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            canvas.drawBitmap(foreground, 0f, 0f, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(foreground, offsetX, offsetY, paint)
        }

        return result
    }

    fun logOom(tag: String, e: Throwable) {
        if (e is OutOfMemoryError) {
            Log.e(tag, "OutOfMemoryError in image processing", e)
        }
    }
}
