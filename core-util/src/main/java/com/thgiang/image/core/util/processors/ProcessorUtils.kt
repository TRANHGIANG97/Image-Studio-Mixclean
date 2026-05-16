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

    fun trimTransparentBounds(
        source: Bitmap,
        alphaThreshold: Int = 96
    ): Bitmap {
        val bitmap = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return bitmap

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rowCounts = IntArray(height)
        val colCounts = IntArray(width)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val alpha = android.graphics.Color.alpha(pixels[rowOffset + x])
                if (alpha > alphaThreshold) {
                    rowCounts[y]++
                    colCounts[x]++
                }
            }
        }

        val minRowPixels = maxOf(2, (width * 0.01f).toInt())
        val minColPixels = maxOf(2, (height * 0.01f).toInt())

        var top = rowCounts.indexOfFirst { it >= minRowPixels }
        var bottom = rowCounts.indexOfLast { it >= minRowPixels }
        var left = colCounts.indexOfFirst { it >= minColPixels }
        var right = colCounts.indexOfLast { it >= minColPixels }

        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            top = height
            bottom = -1
            left = width
            right = -1
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val alpha = android.graphics.Color.alpha(pixels[rowOffset + x])
                    if (alpha > alphaThreshold) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
        }

        if (right < left || bottom < top) {
            return bitmap
        }

        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) {
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    fun hasMeaningfulTransparency(
        bitmap: Bitmap,
        alphaThreshold: Int = 245,
        minTransparentRatio: Float = 0.008f
    ): Boolean {
        if (!bitmap.hasAlpha() || bitmap.width == 0 || bitmap.height == 0) return false

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var transparentPixels = 0
        for (pixel in pixels) {
            if (android.graphics.Color.alpha(pixel) < alphaThreshold) {
                transparentPixels++
            }
        }

        return transparentPixels.toFloat() / pixels.size.toFloat() >= minTransparentRatio
    }

    fun compositeForegroundOverBackground(background: Bitmap, foreground: Bitmap): Bitmap {
        return compositeForegroundOverBackground(background, foreground, 0f, 0f, 1f, 0f)
    }

    fun compositeForegroundOverBackground(
        background: Bitmap,
        foreground: Bitmap,
        offsetX: Float,
        offsetY: Float,
        scale: Float = 1f,
        rotationDegrees: Float = 0f,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false
    ): Bitmap {
        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(background, 0f, 0f, paint)

        val baseScale = if (foreground.width != background.width || foreground.height != background.height) {
            minOf(
                background.width.toFloat() / foreground.width,
                background.height.toFloat() / foreground.height
            )
        } else {
            1f
        }

        val totalScale = baseScale * scale.coerceAtLeast(0.05f)
        val centerX = background.width / 2f + offsetX
        val centerY = background.height / 2f + offsetY

        canvas.save()
        canvas.translate(centerX, centerY)
        if (rotationDegrees != 0f) {
            canvas.rotate(rotationDegrees)
        }
        canvas.scale(
            totalScale * if (flipHorizontal) -1f else 1f,
            totalScale * if (flipVertical) -1f else 1f
        )
        canvas.drawBitmap(
            foreground,
            -foreground.width / 2f,
            -foreground.height / 2f,
            paint
        )
        canvas.restore()

        return result
    }

    fun logOom(tag: String, e: Throwable) {
        if (e is OutOfMemoryError) {
            Log.e(tag, "OutOfMemoryError in image processing", e)
        }
    }
}
