package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Fast Guided Filter implementation theo paper "Fast Guided Filter" (He et al.).
 * Dùng box filter (separable) để tính mean/variance hiệu quả O(n).
 *
 * Đặt trong file riêng: FastGuidedFilter.kt
 * để tránh redeclaration với object GuidedFilter có thể đã tồn tại trong project.
 */
object FastGuidedFilter {

    /**
     * Refine alpha channel dùng guided filter.
     *
     * @param guide Bitmap guide (ảnh gốc), phải là ARGB_8888
     * @param alpha FloatArray alpha/confidence (0-1), size = w * h
     * @param w Width
     * @param h Height
     * @param radius Bán kính window (mặc định 8)
     * @param eps Regularization parameter (mặc định 0.01)
     */
    fun refineAlpha(
        guide: Bitmap,
        alpha: FloatArray,
        w: Int,
        h: Int,
        radius: Int = 8,
        eps: Float = 0.01f
    ): FloatArray {
        require(alpha.size == w * h) { "Alpha size mismatch: ${alpha.size} != ${w * h}" }
        require(guide.width == w && guide.height == h) {
            "Guide bitmap size mismatch: ${guide.width}x${guide.height} vs ${w}x$h"
        }

        // Convert guide to grayscale float array
        val guideGray = FloatArray(w * h)
        val pixels = IntArray(w * h)
        guide.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            guideGray[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        // Box filter mean
        val meanI = boxFilter(guideGray, w, h, radius)
        val meanP = boxFilter(alpha, w, h, radius)
        val meanIP = boxFilter(multiply(guideGray, alpha), w, h, radius)
        val meanII = boxFilter(multiply(guideGray, guideGray), w, h, radius)

        // Variance và covariance
        val varI = FloatArray(w * h)
        val covIP = FloatArray(w * h)
        for (i in varI.indices) {
            varI[i] = meanII[i] - meanI[i] * meanI[i]
            covIP[i] = meanIP[i] - meanI[i] * meanP[i]
        }

        // Linear coefficients
        val a = FloatArray(w * h)
        val b = FloatArray(w * h)
        for (i in a.indices) {
            a[i] = covIP[i] / (varI[i] + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }

        // Mean of coefficients
        val meanA = boxFilter(a, w, h, radius)
        val meanB = boxFilter(b, w, h, radius)

        // Output
        val output = FloatArray(w * h)
        for (i in output.indices) {
            output[i] = meanA[i] * guideGray[i] + meanB[i]
            output[i] = output[i].coerceIn(0f, 1f)
        }

        return output
    }

    /**
     * Box filter (mean filter) dùng prefix sum (separable) để tối ưu O(n).
     * Thay vì O(n * r²), dùng 2 passes O(n * r) với sliding window.
     */
    private fun boxFilter(input: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val output = FloatArray(w * h)
        val temp = FloatArray(w * h)

        // Horizontal pass
        for (y in 0 until h) {
            var sum = 0f
            val initialCount = min(radius + 1, w)
            for (x in 0 until initialCount) {
                sum += input[y * w + x]
            }

            for (x in 0 until w) {
                val left = x - radius - 1
                val right = x + radius

                if (left >= 0) sum -= input[y * w + left]
                if (right < w) sum += input[y * w + right]

                val count = min(right + 1, w) - max(left + 1, 0)
                temp[y * w + x] = sum / count
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var sum = 0f
            val initialCount = min(radius + 1, h)
            for (y in 0 until initialCount) {
                sum += temp[y * w + x]
            }

            for (y in 0 until h) {
                val top = y - radius - 1
                val bottom = y + radius

                if (top >= 0) sum -= temp[top * w + x]
                if (bottom < h) sum += temp[bottom * w + x]

                val count = min(bottom + 1, h) - max(top + 1, 0)
                output[y * w + x] = sum / count
            }
        }

        return output
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == b.size) { "Arrays must have same size" }
        return FloatArray(a.size) { i -> a[i] * b[i] }
    }
}