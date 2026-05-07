package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap

/**
 * Guided Filter for edge-preserving alpha matte refinement.
 *
 * Uses the original RGB bitmap as guidance (I) and the ML Kit confidence mask as input (p)
 * to produce a refined alpha map that preserves high-frequency details (hair, fur, etc.)
 *
 * Based on "Guided Image Filtering" by He, Sun, Tang (ECCV 2010 / TPAMI 2013).
 */
object GuidedFilter {

    /**
     * Refine confidence/alpha values using the original image as guidance.
     * The filter propagates edge information from the RGB image into the alpha map,
     * recovering fine details like individual hair strands.
     *
     * @param bitmap Original full-color bitmap (guidance image)
     * @param confidence Per-pixel subject confidence (0..1), row-major, must be [bitmap.width * bitmap.height]
     * @param w Bitmap / mask width
     * @param h Bitmap / mask height
     * @param radius Local window radius in pixels (default 8; larger = smoother)
     * @param epsilon Edge-preservation sensitivity (default 0.01; lower = sharper edges)
     */
    fun refineAlpha(
        bitmap: Bitmap,
        confidence: FloatArray,
        w: Int,
        h: Int,
        radius: Int = 8,
        epsilon: Float = 0.01f
    ): FloatArray {
        require(confidence.size == w * h) {
            "Confidence array size ${confidence.size} != ${w}x$h"
        }

        // Extract grayscale guidance (luminance, normalized 0..1)
        val guidance = extractLuminance(bitmap, w, h)

        return guidedFilter(guidance, confidence, w, h, radius, epsilon)
    }

    // ── Guided filter core ──────────────────────────────────────────────

    private fun guidedFilter(
        I: FloatArray,   // guidance (0..1)
        p: FloatArray,   // input confidence (0..1)
        w: Int,
        h: Int,
        r: Int,
        eps: Float
    ): FloatArray {
        val n = w * h

        // ── Step 1: box-filtered means ──
        val meanI = boxFilter(I, w, h, r)   // box sum, not yet normalized
        val meanP = boxFilter(p, w, h, r)

        // ── Step 2: correlations ──
        val corrII = FloatArray(n) { I[it] * I[it] }
        val corrIP = FloatArray(n) { I[it] * p[it] }

        val meanII = boxFilter(corrII, w, h, r)
        val meanIP = boxFilter(corrIP, w, h, r)

        // Normalize sums → means
        val windowArea = ((2 * r + 1) * (2 * r + 1)).toFloat()
        for (i in 0 until n) {
            meanI[i] /= windowArea
            meanP[i] /= windowArea
            meanII[i] /= windowArea
            meanIP[i] /= windowArea
        }

        // ── Step 3: linear coefficients a, b ──
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val variance = (meanII[i] - meanI[i] * meanI[i]).coerceAtLeast(0f)
            val covariance = meanIP[i] - meanI[i] * meanP[i]
            a[i] = covariance / (variance + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }

        // ── Step 4: average coefficients ──
        val meanA = boxFilter(a, w, h, r)
        val meanB = boxFilter(b, w, h, r)

        for (i in 0 until n) {
            meanA[i] /= windowArea
            meanB[i] /= windowArea
        }

        // ── Step 5: output q = meanA * I + meanB ──
        val q = FloatArray(n)
        for (i in 0 until n) {
            q[i] = (meanA[i] * I[i] + meanB[i]).coerceIn(0f, 1f)
        }

        return q
    }

    // ── O(1) box filter via summed-area table ──

    private fun boxFilter(input: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        // Build summed-area table (integral image)
        val sat = FloatArray(w * h)
        for (y in 0 until h) {
            var rowSum = 0f
            for (x in 0 until w) {
                val idx = y * w + x
                rowSum += input[idx]
                val above = if (y > 0) sat[idx - w] else 0f
                sat[idx] = rowSum + above
            }
        }

        // Query rectangle sums
        val output = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x1 = (x - r).coerceAtLeast(0)
                val x2 = (x + r).coerceAtMost(w - 1)
                val y1 = (y - r).coerceAtLeast(0)
                val y2 = (y + r).coerceAtMost(h - 1)

                val sx1 = x1 - 1
                val sy1 = y1 - 1

                // S(x2, y2)
                var sum = sat[y2 * w + x2]
                // - S(x1-1, y2)
                if (sx1 >= 0) sum -= sat[y2 * w + sx1]
                // - S(x2, y1-1)
                if (sy1 >= 0) sum -= sat[sy1 * w + x2]
                // + S(x1-1, y1-1)
                if (sx1 >= 0 && sy1 >= 0) sum += sat[sy1 * w + sx1]

                output[y * w + x] = sum
            }
        }

        return output
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun extractLuminance(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        return FloatArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
        }
    }
}
