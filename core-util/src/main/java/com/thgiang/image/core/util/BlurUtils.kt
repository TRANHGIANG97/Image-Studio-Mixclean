package com.thgiang.image.core.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.min
import kotlin.math.roundToInt

suspend fun blurBitmapForPortraitExport(bitmap: Bitmap, blurRadius: Float): Bitmap {
    val r = blurRadius.coerceIn(0f, 25f)
    if (r < 0.5f) {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
    return AdvancedBlurTransformation(r).transform(bitmap, Size(bitmap.width, bitmap.height))
}

class AdvancedBlurTransformation(
    private val radius: Float
) : Transformation {

    override val cacheKey: String = "advanced_boxblur_premul_v1_r=${radius.roundToInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val r = radius.coerceIn(0f, 25f)
        if (r < 0.5f) return input

        val radiusInt = r.roundToInt().coerceIn(1, 25)
        val sampling = when {
            radiusInt > 16 -> 4
            radiusInt > 10 -> 2
            else -> 1
        }

        if (sampling == 1) {
            return blurBoxPremultiplied(input, radiusInt)
        }

        val sw = (input.width / sampling).coerceAtLeast(1)
        val sh = (input.height / sampling).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, sw, sh, true)
        val blurredSmall = blurBoxPremultiplied(small, (radiusInt / sampling).coerceAtLeast(1))
        if (blurredSmall !== small && !small.isRecycled) small.recycle()

        val full = Bitmap.createScaledBitmap(blurredSmall, input.width, input.height, true)
        if (blurredSmall !== full && !blurredSmall.isRecycled) blurredSmall.recycle()
        return full
    }

    private fun blurBoxPremultiplied(input: Bitmap, radius: Int): Bitmap {
        val bitmap = input.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        premultiplyAlpha(pixels)
        boxBlur(pixels, w, h, radius.coerceIn(1, min(w, h) / 2))
        unpremultiplyAlpha(pixels)

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun premultiplyAlpha(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            if (a == 255) continue
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val pr = (r * a + 127) / 255
            val pg = (g * a + 127) / 255
            val pb = (b * a + 127) / 255
            pixels[i] = (a shl 24) or (pr shl 16) or (pg shl 8) or pb
        }
    }

    private fun unpremultiplyAlpha(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            if (a == 255) continue
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val ur = ((r * 255 + (a / 2)) / a).coerceIn(0, 255)
            val ug = ((g * 255 + (a / 2)) / a).coerceIn(0, 255)
            val ub = ((b * 255 + (a / 2)) / a).coerceIn(0, 255)
            pixels[i] = (a shl 24) or (ur shl 16) or (ug shl 8) or ub
        }
    }

    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
        if (radius < 1) return
        val temp = IntArray(pixels.size)
        boxBlurHorizontal(pixels, temp, w, h, radius)
        boxBlurVertical(temp, pixels, w, h, radius)
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius + radius + 1
        for (y in 0 until h) {
            val row = y * w
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0

            val first = src[row]
            val last = src[row + w - 1]

            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, w - 1)]
                aSum += (p ushr 24) and 0xFF
                rSum += (p ushr 16) and 0xFF
                gSum += (p ushr 8) and 0xFF
                bSum += p and 0xFF
            }

            for (x in 0 until w) {
                dst[row + x] = ((aSum / div) shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)

                val outX = x - radius
                val inX = x + radius + 1

                val outP = if (outX < 0) first else src[row + outX]
                val inP = if (inX >= w) last else src[row + inX]

                aSum += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                rSum += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                gSum += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                bSum += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius + radius + 1
        for (x in 0 until w) {
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0

            val first = src[x]
            val last = src[(h - 1) * w + x]

            for (i in -radius..radius) {
                val y = i.coerceIn(0, h - 1)
                val p = src[y * w + x]
                aSum += (p ushr 24) and 0xFF
                rSum += (p ushr 16) and 0xFF
                gSum += (p ushr 8) and 0xFF
                bSum += p and 0xFF
            }

            for (y in 0 until h) {
                dst[y * w + x] = ((aSum / div) shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)

                val outY = y - radius
                val inY = y + radius + 1

                val outP = if (outY < 0) first else src[outY * w + x]
                val inP = if (inY >= h) last else src[inY * w + x]

                aSum += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                rSum += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                gSum += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                bSum += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }
}
