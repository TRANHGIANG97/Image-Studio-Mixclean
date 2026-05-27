package com.thgiang.image.core.data.backgroundremove

import android.graphics.Rect
import kotlin.math.abs

object MaskQualityAnalyzer {

    enum class Recommendation {
        USE_MODNET_ONLY,
        USE_MLKIT_ONLY,
        FUSE_HYBRID,
    }

    data class MaskQuality(
        val recommendation: Recommendation,
        val centerDensity: Float,
        val centerHoleRatio: Float,
        val coverageRatio: Float,
        val edgeNoiseScore: Float,
        val lowerWeakGapRatio: Float,
    )

    fun analyze(mask: PortraitConfidenceMask): MaskQuality {
        require(mask.width > 0 && mask.height > 0)
        require(mask.values.size == mask.width * mask.height)

        val values = mask.values
        val width = mask.width
        val height = mask.height
        val center = computeCenterRegion(width, height, ratioW = 0.42f, ratioH = 0.50f)

        var centerSum = 0f
        var centerCount = 0
        var centerHoles = 0

        val stepX = (width / 128).coerceAtLeast(2)
        val stepY = (height / 128).coerceAtLeast(2)

        for (y in center.top until center.bottom step stepY) {
            val row = y * width
            for (x in center.left until center.right step stepX) {
                val alpha = values[row + x].coerceIn(0f, 1f)
                centerSum += alpha
                centerCount++
                if (alpha < 0.30f) centerHoles++
            }
        }

        val centerDensity = if (centerCount > 0) centerSum / centerCount else 0f
        val centerHoleRatio = if (centerCount > 0) centerHoles.toFloat() / centerCount else 1f

        var foregroundCount = 0
        for (value in values) {
            if (value > 0.50f) foregroundCount++
        }
        val coverageRatio = foregroundCount.toFloat() / values.size.toFloat()
        val edgeNoiseScore = computeEdgeNoise(values, width, height)
        val lowerWeakGapRatio = computeLowerWeakGapRatio(values, width, height)

        val recommendation = when {
            coverageRatio < 0.01f && centerDensity < 0.05f -> Recommendation.USE_MLKIT_ONLY
            coverageRatio < 0.025f -> Recommendation.USE_MLKIT_ONLY
            centerDensity < 0.20f -> Recommendation.USE_MLKIT_ONLY
            lowerWeakGapRatio > 0.16f && coverageRatio > 0.10f -> Recommendation.FUSE_HYBRID
            centerHoleRatio > 0.18f -> Recommendation.FUSE_HYBRID
            centerDensity < 0.45f && coverageRatio < 0.12f -> Recommendation.FUSE_HYBRID
            edgeNoiseScore > 0.38f && centerDensity < 0.58f -> Recommendation.FUSE_HYBRID
            else -> Recommendation.USE_MODNET_ONLY
        }

        return MaskQuality(
            recommendation = recommendation,
            centerDensity = centerDensity,
            centerHoleRatio = centerHoleRatio,
            coverageRatio = coverageRatio,
            edgeNoiseScore = edgeNoiseScore,
            lowerWeakGapRatio = lowerWeakGapRatio,
        )
    }

    private fun computeCenterRegion(
        width: Int,
        height: Int,
        ratioW: Float,
        ratioH: Float,
    ): Rect {
        val w = (width * ratioW).toInt().coerceIn(1, width)
        val h = (height * ratioH).toInt().coerceIn(1, height)
        val left = ((width - w) / 2).coerceAtLeast(0)
        val top = ((height - h) / 2).coerceAtLeast(0)
        return Rect(left, top, left + w, top + h)
    }

    private fun computeEdgeNoise(
        values: FloatArray,
        width: Int,
        height: Int,
    ): Float {
        if (width < 4 || height < 4) return 0f

        var noisy = 0
        var checked = 0

        val stepX = (width / 160).coerceAtLeast(2)
        val stepY = (height / 160).coerceAtLeast(2)

        for (y in 1 until height - 1 step stepY) {
            val row = y * width
            for (x in 1 until width - 1 step stepX) {
                val index = row + x
                val alpha = values[index]
                if (alpha !in 0.08f..0.92f) continue

                val gradient = abs(alpha - values[index + 1]) + abs(alpha - values[index + width])
                checked++
                if (gradient > 0.55f) noisy++
            }
        }

        return if (checked == 0) 0f else noisy.toFloat() / checked
    }

    private fun computeLowerWeakGapRatio(
        values: FloatArray,
        width: Int,
        height: Int,
    ): Float {
        if (width <= 0 || height <= 0) return 0f

        val top = (height * 0.42f).toInt().coerceIn(0, height - 1)
        val bottom = (height * 0.96f).toInt().coerceIn(top + 1, height)

        var weakCount = 0
        var total = 0

        val stepX = (width / 192).coerceAtLeast(2)
        val stepY = (height / 192).coerceAtLeast(2)

        for (y in top until bottom step stepY) {
            val row = y * width
            for (x in 0 until width step stepX) {
                val alpha = values[row + x].coerceIn(0f, 1f)

                if (alpha in 0.16f..0.68f) {
                    weakCount++
                }

                total++
            }
        }

        return if (total == 0) 0f else weakCount.toFloat() / total.toFloat()
    }
}
