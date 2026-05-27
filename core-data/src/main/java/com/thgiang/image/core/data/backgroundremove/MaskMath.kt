package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

object MaskMath {
    private const val ENABLE_COLOR_FRINGE_SUPPRESSION = true

    fun resizeBilinear(
        mask: PortraitConfidenceMask,
        targetWidth: Int,
        targetHeight: Int,
    ): PortraitConfidenceMask {
        return PortraitConfidenceMask(
            width = targetWidth,
            height = targetHeight,
            values = resizeBilinear(
                src = mask.values,
                srcW = mask.width,
                srcH = mask.height,
                dstW = targetWidth,
                dstH = targetHeight,
            ),
        )
    }

    fun resizeBilinear(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): FloatArray {
        require(srcW > 0 && srcH > 0 && dstW > 0 && dstH > 0)
        require(src.size == srcW * srcH) {
            "Mask size mismatch: data=${src.size}, expected=${srcW * srcH}"
        }

        if (srcW == dstW && srcH == dstH) {
            return src.copyOf()
        }

        val out = FloatArray(dstW * dstH)
        if (srcW == 1 || srcH == 1 || dstW == 1 || dstH == 1) {
            out.fill(src.firstOrNull()?.coerceIn(0f, 1f) ?: 0f)
            return out
        }

        val xRatio = (srcW - 1).toFloat() / (dstW - 1).toFloat()
        val yRatio = (srcH - 1).toFloat() / (dstH - 1).toFloat()

        for (dy in 0 until dstH) {
            val sy = dy * yRatio
            val y0 = floor(sy).toInt()
            val y1 = min(y0 + 1, srcH - 1)
            val wy = sy - y0
            val dstRow = dy * dstW

            for (dx in 0 until dstW) {
                val sx = dx * xRatio
                val x0 = floor(sx).toInt()
                val x1 = min(x0 + 1, srcW - 1)
                val wx = sx - x0

                val top = src[y0 * srcW + x0] * (1f - wx) + src[y0 * srcW + x1] * wx
                val bottom = src[y1 * srcW + x0] * (1f - wx) + src[y1 * srcW + x1] * wx

                out[dstRow + dx] = (top * (1f - wy) + bottom * wy).coerceIn(0f, 1f)
            }
        }

        return out
    }

    fun resizeToMaxSide(
        mask: PortraitConfidenceMask,
        maxSide: Int,
    ): PortraitConfidenceMask {
        require(maxSide > 0)

        val srcMax = maxOf(mask.width, mask.height)
        if (srcMax <= maxSide) {
            return PortraitConfidenceMask(mask.width, mask.height, mask.values.copyOf())
        }

        val scale = maxSide.toFloat() / srcMax.toFloat()
        val targetW = (mask.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (mask.height * scale).roundToInt().coerceAtLeast(1)
        return resizeBilinear(mask, targetW, targetH)
    }

    fun ensureMaskSize(
        mask: PortraitConfidenceMask,
        width: Int,
        height: Int,
    ): PortraitConfidenceMask {
        return if (mask.width == width && mask.height == height) {
            PortraitConfidenceMask(mask.width, mask.height, mask.values.copyOf())
        } else {
            resizeBilinear(mask, width, height)
        }
    }

    fun applyMaskToBitmap(
        bitmap: Bitmap,
        mask: PortraitConfidenceMask,
    ): Bitmap {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(mask.width == bitmap.width && mask.height == bitmap.height) {
            "Mask ${mask.width}x${mask.height} != Bitmap ${bitmap.width}x${bitmap.height}"
        }
        require(mask.values.size == bitmap.width * bitmap.height)

        val width = bitmap.width
        val height = bitmap.height
        val srcPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
        for (i in srcPixels.indices) {
            val src = srcPixels[i]
            val srcAlpha = (src ushr 24) and 0xFF
            val maskAlpha = (mask.values[i].coerceIn(0f, 1f) * 255f).roundToInt()
            val finalAlpha = (srcAlpha * maskAlpha / 255f).roundToInt().coerceIn(0, 255)
            outPixels[i] = (finalAlpha shl 24) or (src and 0x00FFFFFF)
        }

        if (ENABLE_COLOR_FRINGE_SUPPRESSION) {
            suppressColorFringe(outPixels, mask.values, width, height)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(true)
            setPixels(outPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun suppressColorFringe(
        pixels: IntArray,
        mask: FloatArray,
        width: Int,
        height: Int,
    ) {
        val source = pixels.copyOf()

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                val alpha = (source[index] ushr 24) and 0xFF

                if (alpha <= 6) {
                    pixels[index] = 0
                    continue
                }

                if (alpha >= 245) continue

                var redSum = 0
                var greenSum = 0
                var blueSum = 0
                var weightSum = 0

                for (dy in -2..2) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue

                    for (dx in -2..2) {
                        val nx = x + dx
                        if (nx !in 0 until width || dx * dx + dy * dy > 8) continue

                        val neighborIndex = ny * width + nx
                        if (mask[neighborIndex] < 0.88f) continue

                        val neighbor = source[neighborIndex]
                        val neighborAlpha = (neighbor ushr 24) and 0xFF
                        if (neighborAlpha < 220) continue

                        val weight = neighborAlpha * (5 - kotlin.math.abs(dx) - kotlin.math.abs(dy)).coerceAtLeast(1)
                        redSum += ((neighbor shr 16) and 0xFF) * weight
                        greenSum += ((neighbor shr 8) and 0xFF) * weight
                        blueSum += (neighbor and 0xFF) * weight
                        weightSum += weight
                    }
                }

                if (weightSum > 0) {
                    val red = (redSum / weightSum).coerceIn(0, 255)
                    val green = (greenSum / weightSum).coerceIn(0, 255)
                    val blue = (blueSum / weightSum).coerceIn(0, 255)
                    pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
        }
    }

    fun foregroundCoverage(
        mask: PortraitConfidenceMask,
        threshold: Float = 0.5f,
    ): Float {
        if (mask.values.isEmpty()) return 0f
        var count = 0
        for (value in mask.values) {
            if (value > threshold) count++
        }
        return count.toFloat() / mask.values.size.toFloat()
    }
}
