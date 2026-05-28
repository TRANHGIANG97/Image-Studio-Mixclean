package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Mask(
    val width: Int,
    val height: Int,
    val data: FloatArray,
) {
    init {
        require(width > 0 && height > 0)
        require(data.size == width * height)
    }

    fun crop(x: Int, y: Int, width: Int, height: Int): Mask {
        require(width > 0 && height > 0)
        require(x >= 0 && y >= 0)
        require(x + width <= this.width) { "Crop width exceeds mask width" }
        require(y + height <= this.height) { "Crop height exceeds mask height" }

        val out = FloatArray(width * height)
        for (yy in 0 until height) {
            val srcY = y + yy
            val dstRow = yy * width
            for (xx in 0 until width) {
                out[dstRow + xx] = data[srcY * this.width + x + xx]
            }
        }
        return Mask(width, height, out)
    }

    fun resizeBilinear(targetWidth: Int, targetHeight: Int): Mask {
        require(targetWidth > 0 && targetHeight > 0)
        if (targetWidth == width && targetHeight == height) return this

        val out = FloatArray(targetWidth * targetHeight)
        if (width <= 1 || height <= 1 || targetWidth <= 1 || targetHeight <= 1) {
            out.fill(data.firstOrNull() ?: 0f)
            return Mask(targetWidth, targetHeight, out)
        }

        val xRatio = (width - 1).toFloat() / (targetWidth - 1)
        val yRatio = (height - 1).toFloat() / (targetHeight - 1)
        for (ty in 0 until targetHeight) {
            val srcY = ty * yRatio
            val y0 = floor(srcY).toInt()
            val y1 = min(y0 + 1, height - 1)
            val wy = srcY - y0
            val dstRow = ty * targetWidth
            for (tx in 0 until targetWidth) {
                val srcX = tx * xRatio
                val x0 = floor(srcX).toInt()
                val x1 = min(x0 + 1, width - 1)
                val wx = srcX - x0
                val top = data[y0 * width + x0] * (1f - wx) + data[y0 * width + x1] * wx
                val bottom = data[y1 * width + x0] * (1f - wx) + data[y1 * width + x1] * wx
                out[dstRow + tx] = (top * (1f - wy) + bottom * wy).coerceIn(0f, 1f)
            }
        }
        return Mask(targetWidth, targetHeight, out)
    }

    fun blurBoxSeparable(radius: Int): Mask {
        if (radius <= 0) return this

        val temp = FloatArray(width * height)
        val out = FloatArray(width * height)
        val kernel = radius * 2 + 1

        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (kx in -radius..radius) {
                sum += data[row + kx.coerceIn(0, width - 1)]
            }
            for (x in 0 until width) {
                temp[row + x] = sum / kernel
                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                sum += data[row + addX] - data[row + removeX]
            }
        }

        for (x in 0 until width) {
            var sum = 0f
            for (ky in -radius..radius) {
                sum += temp[ky.coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                out[y * width + x] = (sum / kernel).coerceIn(0f, 1f)
                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                sum += temp[addY * width + x] - temp[removeY * width + x]
            }
        }

        return Mask(width, height, out)
    }

    fun foregroundCoverage(threshold: Float = 0.5f): Float {
        var count = 0
        for (value in data) {
            if (value >= threshold) count++
        }
        return count.toFloat() / max(1, data.size)
    }

    fun calculateCertaintyPercent(): Int {
        var sum = 0f
        for (value in data) {
            sum += kotlin.math.abs(value - 0.5f) * 2f
        }
        return ((sum / max(1, data.size)) * 100f).roundToInt().coerceIn(0, 100)
    }
}

fun applyMaskToImage(image: Bitmap, mask: Mask): Bitmap {
    require(mask.width == image.width && mask.height == image.height) {
        "Mask size ${mask.width}x${mask.height} != image ${image.width}x${image.height}"
    }

    val width = image.width
    val height = image.height
    val srcPixels = IntArray(width * height)
    image.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(width * height)

    for (i in srcPixels.indices) {
        val src = srcPixels[i]
        val srcAlpha = (src ushr 24) and 0xFF
        val maskAlpha = (mask.data[i] * 255f).roundToInt().coerceIn(0, 255)
        val alpha = (srcAlpha * maskAlpha / 255f).roundToInt().coerceIn(0, 255)
        val r = (src shr 16) and 0xFF
        val g = (src shr 8) and 0xFF
        val b = src and 0xFF
        outPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(outPixels, 0, width, 0, 0, width, height)
    }
}

fun Mask.toPreviewBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    for (i in data.indices) {
        val value = (data[i].coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        pixels[i] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
