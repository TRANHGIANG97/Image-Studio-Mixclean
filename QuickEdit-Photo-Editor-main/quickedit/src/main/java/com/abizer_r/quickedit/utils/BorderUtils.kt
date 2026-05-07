package com.abizer_r.quickedit.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BorderUtils {

    suspend fun applyBorderToBitmap(
        bitmap: Bitmap,
        borderColorArgb: Int,
        borderWidthPx: Int = 24,
        previewMaxDimension: Int? = null
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val w = bitmap.width
            val h = bitmap.height
            val R = borderWidthPx.coerceAtLeast(1)
            val targetOutW = w + 2 * R
            val targetOutH = h + 2 * R

            when {
                previewMaxDimension != null && maxOf(w, h) > previewMaxDimension -> {
                    val scale = previewMaxDimension.toFloat() / maxOf(w, h)
                    val smallW = (w * scale).toInt().coerceAtLeast(1)
                    val smallH = (h * scale).toInt().coerceAtLeast(1)
                    val smallR = (R * scale).toInt().coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
                    val bordered = applyBorderToBitmapInternal(small, borderColorArgb, smallR)
                    if (small != bitmap) small.recycle()
                    val upscaled = Bitmap.createScaledBitmap(bordered, targetOutW, targetOutH, true)
                    bordered.recycle()
                    upscaled
                }
                else -> applyBorderToBitmapInternal(bitmap, borderColorArgb, R)
            }
        }
    }

    private fun applyBorderToBitmapInternal(
        bitmap: Bitmap,
        borderColorArgb: Int,
        borderWidthPx: Int
    ): Bitmap {
        val R = borderWidthPx.coerceAtLeast(1)
        val w = bitmap.width
        val h = bitmap.height
        val outW = w + 2 * R
        val outH = h + 2 * R

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val origAlpha = IntArray(w * h)
        for (i in pixels.indices) origAlpha[i] = Color.alpha(pixels[i])
        boxBlur(origAlpha, w, h, 2)

        val extAlpha = IntArray(outW * outH)
        for (y in 0 until h) {
            for (x in 0 until w) {
                extAlpha[(y + R) * outW + (x + R)] = origAlpha[y * w + x]
            }
        }
        dilate2DSeparable(extAlpha, outW, outH, R)
        
        boxBlur(extAlpha, outW, outH, 2)

        val r = Color.red(borderColorArgb)
        val g = Color.green(borderColorArgb)
        val b = Color.blue(borderColorArgb)
        
        val outPixels = IntArray(outW * outH)
        for (j in 0 until outH) {
            for (i in 0 until outW) {
                val sx = i - R
                val sy = j - R
                
                val oa = if (sx in 0 until w && sy in 0 until h) origAlpha[sy * w + sx] else 0
                val da = extAlpha[j * outW + i]
                
                outPixels[j * outW + i] = when {
                    oa > 200 -> pixels[sy * w + sx]
                    oa > 0 -> {
                        val f = oa / 255f
                        val orig = pixels[sy * w + sx]
                        val mixR = (Color.red(orig) * f + r * (1 - f)).toInt().coerceIn(0, 255)
                        val mixG = (Color.green(orig) * f + g * (1 - f)).toInt().coerceIn(0, 255)
                        val mixB = (Color.blue(orig) * f + b * (1 - f)).toInt().coerceIn(0, 255)
                        Color.argb(maxOf(oa, da), mixR, mixG, mixB)
                    }
                    da > 0 -> Color.argb(da, r, g, b)
                    else -> Color.TRANSPARENT
                }
            }
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        return out
    }

    private fun boxBlur(data: IntArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        val temp = IntArray(data.size)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (kx in -radius..radius) {
                    val nx = x + kx
                    if (nx in 0 until width) {
                        sum += data[offset + nx]
                        count++
                    }
                }
                temp[offset + x] = sum / count
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) {
                var sum = 0
                var count = 0
                for (ky in -radius..radius) {
                    val ny = y + ky
                    if (ny in 0 until height) {
                        sum += temp[ny * width + x]
                        count++
                    }
                }
                data[y * width + x] = sum / count
            }
        }
    }

    private fun dilate2DSeparable(data: IntArray, width: Int, height: Int, radius: Int) {
        val temp = IntArray(data.size)
        val r = radius.coerceIn(0, maxOf(width, height))
        for (y in 0 until height) {
            dilate1DMax(data, temp, y * width, width, r)
        }
        for (x in 0 until width) {
            dilate1DColumnMax(temp, data, x, width, height, r)
        }
    }

    private fun dilate1DMax(src: IntArray, dst: IntArray, offset: Int, length: Int, radius: Int) {
        val d = ArrayDeque<Int>()
        for (i in 0 until length) {
            val windowStart = (i - radius).coerceAtLeast(0)
            while (d.isNotEmpty() && d.first() < windowStart) d.removeFirst()
            val j = i + radius
            if (j < length) {
                val v = src[offset + j]
                while (d.isNotEmpty() && src[offset + d.last()] <= v) d.removeLast()
                d.addLast(j)
            }
            dst[offset + i] = if (d.isEmpty()) 0 else src[offset + d.first()]
        }
    }

    private fun dilate1DColumnMax(
        src: IntArray,
        dst: IntArray,
        col: Int,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val d = ArrayDeque<Int>()
        for (i in 0 until height) {
            val windowStart = (i - radius).coerceAtLeast(0)
            while (d.isNotEmpty() && d.first() < windowStart) d.removeFirst()
            val j = i + radius
            if (j < height) {
                val v = src[j * width + col]
                while (d.isNotEmpty() && src[d.last() * width + col] <= v) d.removeLast()
                d.addLast(j)
            }
            val idx = i * width + col
            dst[idx] = if (d.isEmpty()) 0 else src[d.first() * width + col]
        }
    }
}
