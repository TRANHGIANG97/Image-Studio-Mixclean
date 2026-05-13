package com.abizer_r.quickedit.utils.effectsMode

import android.content.Context
import android.graphics.Bitmap

object BitmapBlurFilter {
    fun apply(context: Context, original: Bitmap): Bitmap {
        return runCatching {
            applyFallbackBlur(original, radius = 5)
        }.getOrElse {
            original.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun applyFallbackBlur(original: Bitmap, radius: Int): Bitmap {
        val src = if (original.config == Bitmap.Config.ARGB_8888) {
            original
        } else {
            original.copy(Bitmap.Config.ARGB_8888, false)
        }
        val working = if (src.isMutable) src.copy(Bitmap.Config.ARGB_8888, true) else src.copy(Bitmap.Config.ARGB_8888, true)
        val w = working.width
        val h = working.height
        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)

        val r = radius.coerceAtLeast(1)
        val out = IntArray(pixels.size)

        for (y in 0 until h) {
            val yStart = maxOf(0, y - r)
            val yEnd = minOf(h - 1, y + r)
            for (x in 0 until w) {
                val xStart = maxOf(0, x - r)
                val xEnd = minOf(w - 1, x + r)
                var a = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (yy in yStart..yEnd) {
                    val row = yy * w
                    for (xx in xStart..xEnd) {
                        val c = pixels[row + xx]
                        a += c ushr 24 and 0xff
                        red += c ushr 16 and 0xff
                        green += c ushr 8 and 0xff
                        blue += c and 0xff
                        count++
                    }
                }
                val idx = y * w + x
                out[idx] = (a / count shl 24) or (red / count shl 16) or (green / count shl 8) or (blue / count)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        if (working !== original && !working.isRecycled) working.recycle()
        return result
    }
}
