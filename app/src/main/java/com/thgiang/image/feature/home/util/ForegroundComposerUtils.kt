package com.thgiang.image.feature.home.util
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.min
import kotlin.math.sqrt

internal fun normalizeForegroundForCompositing(foreground: Bitmap): Bitmap {
    val source = if (foreground.config != Bitmap.Config.ARGB_8888) {
        foreground.copy(Bitmap.Config.ARGB_8888, true)
    } else {
        foreground
    }

    // If remover already produced transparency, keep it as-is.
    if (computeTransparencyRatio(source) >= 0.008f) {
        return source
    }

    // Fallback matting for cases where remover returns nearly opaque foreground.
    val out = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
    val width = out.width
    val height = out.height
    val pixels = IntArray(width * height)
    out.getPixels(pixels, 0, width, 0, 0, width, height)

    val edgeInset = (min(width, height) * 0.01f).toInt().coerceAtLeast(1)
    val edge = estimateEdgeColor(pixels, width, height, edgeInset)
    val edgeR = Color.red(edge)
    val edgeG = Color.green(edge)
    val edgeB = Color.blue(edge)

    val low = 26.0
    val high = 56.0

    for (idx in pixels.indices) {
        val p = pixels[idx]
        val a = Color.alpha(p)
        if (a == 0) continue

        val r = Color.red(p)
        val g = Color.green(p)
        val b = Color.blue(p)

        val dist = colorDistance(r, g, b, edgeR, edgeG, edgeB)
        val newAlpha = when {
            dist <= low -> 0
            dist >= high -> a
            else -> {
                val t = ((dist - low) / (high - low)).coerceIn(0.0, 1.0)
                (a * t).toInt().coerceIn(0, 255)
            }
        }

        pixels[idx] = Color.argb(newAlpha, r, g, b)
    }

    out.setPixels(pixels, 0, width, 0, 0, width, height)
    return out
}

internal fun computeTransparencyRatio(bitmap: Bitmap): Float {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return 0f

    val total = width * height
    val pixels = IntArray(total)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var transparent = 0
    for (p in pixels) {
        if (Color.alpha(p) < 245) transparent++
    }
    return transparent.toFloat() / total.toFloat()
}

private fun estimateEdgeColor(
    pixels: IntArray,
    width: Int,
    height: Int,
    edgeInset: Int
): Int {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L

    fun sample(x: Int, y: Int) {
        val p = pixels[y * width + x]
        rSum += Color.red(p)
        gSum += Color.green(p)
        bSum += Color.blue(p)
        count++
    }

    val stepX = (width / 160).coerceAtLeast(1)
    val stepY = (height / 160).coerceAtLeast(1)

    for (x in 0 until width step stepX) {
        for (e in 0 until edgeInset) {
            sample(x, e)
            sample(x, height - 1 - e)
        }
    }
    for (y in 0 until height step stepY) {
        for (e in 0 until edgeInset) {
            sample(e, y)
            sample(width - 1 - e, y)
        }
    }

    if (count == 0L) return Color.WHITE
    return Color.rgb(
        (rSum / count).toInt().coerceIn(0, 255),
        (gSum / count).toInt().coerceIn(0, 255),
        (bSum / count).toInt().coerceIn(0, 255)
    )
}

private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
    val dr = (r1 - r2).toDouble()
    val dg = (g1 - g2).toDouble()
    val db = (b1 - b2).toDouble()
    return sqrt(dr * dr + dg * dg + db * db)
}




