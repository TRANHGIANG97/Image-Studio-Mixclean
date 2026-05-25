package com.abizer_r.quickedit.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.abizer_r.quickedit.utils.BorderGradientDirection
import com.abizer_r.quickedit.utils.BorderGradientPreset
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

object BorderUtils {

    private const val MAX_BORDER_DIMENSION = 4096
    private const val CANCEL_CHECK_MASK = 0x3F

    suspend fun applyBorderToBitmap(
        bitmap: Bitmap,
        borderColorArgb: Int,
        borderGradientPreset: BorderGradientPreset? = null,
        borderWidthPx: Int = 24,
        borderBlurRadiusPx: Int = 4,
        borderPreset: BorderPreset = BorderPreset.SOLID,
        previewMaxDimension: Int? = null
    ): Result<Bitmap> {
        return try {
            coroutineContext.ensureActive()

            val width = bitmap.width
            val height = bitmap.height
            val safeBorderWidth = borderWidthPx.coerceAtLeast(1)
            val maxSide = maxOf(width, height)
            val maxDimension = previewMaxDimension ?: MAX_BORDER_DIMENSION
            val scale = if (maxSide > maxDimension) {
                maxDimension.toFloat() / maxSide
            } else {
                1f
            }

            val workingBitmap = if (scale < 1f) {
                val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
                val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            } else {
                bitmap
            }

            try {
                val workingBorderWidth = if (scale < 1f) {
                    (safeBorderWidth * scale).roundToInt().coerceAtLeast(1)
                } else {
                    safeBorderWidth
                }

                val bordered = renderBorderedBitmap(
                    bitmap = workingBitmap,
                    borderColorArgb = borderColorArgb,
                    borderGradientPreset = borderGradientPreset,
                    borderWidthPx = workingBorderWidth,
                    borderBlurRadiusPx = if (scale < 1f) {
                        (borderBlurRadiusPx * scale).roundToInt().coerceAtLeast(0)
                    } else {
                        borderBlurRadiusPx
                    },
                    borderPreset = borderPreset
                )

                if (scale < 1f && previewMaxDimension == null) {
                    val targetWidth = width + 2 * safeBorderWidth
                    val targetHeight = height + 2 * safeBorderWidth
                    try {
                        coroutineContext.ensureActive()
                        Result.success(
                            Bitmap.createScaledBitmap(bordered, targetWidth, targetHeight, true)
                        )
                    } finally {
                        bordered.recycle()
                    }
                } else {
                    Result.success(bordered)
                }
            } finally {
                if (workingBitmap !== bitmap) {
                    workingBitmap.recycle()
                }
            }
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun renderBorderedBitmap(
        bitmap: Bitmap,
        borderColorArgb: Int,
        borderGradientPreset: BorderGradientPreset?,
        borderWidthPx: Int,
        borderBlurRadiusPx: Int,
        borderPreset: BorderPreset
    ): Bitmap {
        coroutineContext.ensureActive()

        val width = bitmap.width
        val height = bitmap.height
        val padding = borderWidthPx.coerceAtLeast(1)
        val outW = width + 2 * padding
        val outH = height + 2 * padding

        val sourcePixels = IntArray(width * height)
        bitmap.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        coroutineContext.ensureActive()

        val sourceAlpha = IntArray(sourcePixels.size)
        for (i in sourcePixels.indices) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            sourceAlpha[i] = Color.alpha(sourcePixels[i])
        }

        val profile = resolveProfile(borderPreset, padding, borderBlurRadiusPx)
        if (profile.sourceBlurRadius > 0) {
            boxBlur(sourceAlpha, width, height, profile.sourceBlurRadius)
        }

        val compositeAlpha = IntArray(outW * outH)
        for (layer in profile.layers) {
            coroutineContext.ensureActive()
            val layerAlpha = buildLayerMask(
                sourceAlpha = sourceAlpha,
                sourceWidth = width,
                sourceHeight = height,
                outWidth = outW,
                outHeight = outH,
                padding = padding,
                radius = layer.radius
            )

            if (layer.blurRadius > 0) {
                boxBlur(layerAlpha, outW, outH, layer.blurRadius)
            }

            if (layer.alphaMultiplier != 1f) {
                for (i in layerAlpha.indices) {
                    if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
                    layerAlpha[i] = (
                        layerAlpha[i] * layer.alphaMultiplier
                    ).roundToInt().coerceIn(0, 255)
                }
            }

            for (i in layerAlpha.indices) {
                if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
                if (layerAlpha[i] > compositeAlpha[i]) {
                    compositeAlpha[i] = layerAlpha[i]
                }
            }
        }

        val red = Color.red(borderColorArgb)
        val green = Color.green(borderColorArgb)
        val blue = Color.blue(borderColorArgb)

        val borderPixels = IntArray(outW * outH)
        for (i in compositeAlpha.indices) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val alpha = compositeAlpha[i].coerceIn(0, 255)
            if (alpha > 0) {
                val x = i % outW
                val y = i / outW
                val colorArgb = if (borderGradientPreset != null) {
                    colorForGradientPosition(
                        preset = borderGradientPreset,
                        x = x,
                        y = y,
                        width = outW,
                        height = outH,
                        alpha = alpha
                    )
                } else {
                    Color.argb(alpha, red, green, blue)
                }
                borderPixels[i] = colorArgb
            }
        }

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        output.setPixels(borderPixels, 0, outW, 0, 0, outW, outH)
        Canvas(output).drawBitmap(bitmap, padding.toFloat(), padding.toFloat(), null)
        return output
    }

    private fun colorForGradientPosition(
        preset: BorderGradientPreset,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        alpha: Int
    ): Int {
        val usableWidth = max(1, width - 1)
        val usableHeight = max(1, height - 1)
        val fraction = when (preset.direction) {
            BorderGradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> {
                ((x.toFloat() / usableWidth) + (y.toFloat() / usableHeight)) / 2f
            }
            BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> {
                ((x.toFloat() / usableWidth) + (1f - (y.toFloat() / usableHeight))) / 2f
            }
        }.coerceIn(0f, 1f)

        val gradientColor = lerpGradientColors(preset.colors, fraction)
        return Color.argb(alpha, Color.red(gradientColor), Color.green(gradientColor), Color.blue(gradientColor))
    }

    private fun lerpGradientColors(colors: IntArray, fraction: Float): Int {
        if (colors.isEmpty()) return Color.BLACK
        if (colors.size == 1) return colors[0]

        val clamped = fraction.coerceIn(0f, 1f)
        val scaled = clamped * (colors.size - 1)
        val leftIndex = floor(scaled).toInt().coerceIn(0, colors.lastIndex)
        val rightIndex = min(leftIndex + 1, colors.lastIndex)
        if (leftIndex == rightIndex) return colors[leftIndex]

        val localT = scaled - leftIndex
        return blendColors(colors[leftIndex], colors[rightIndex], localT)
    }

    private fun blendColors(startColor: Int, endColor: Int, t: Float): Int {
        val clampedT = t.coerceIn(0f, 1f)
        val invT = 1f - clampedT
        val a = (Color.alpha(startColor) * invT + Color.alpha(endColor) * clampedT).roundToInt()
        val r = (Color.red(startColor) * invT + Color.red(endColor) * clampedT).roundToInt()
        val g = (Color.green(startColor) * invT + Color.green(endColor) * clampedT).roundToInt()
        val b = (Color.blue(startColor) * invT + Color.blue(endColor) * clampedT).roundToInt()
        return Color.argb(a, r, g, b)
    }

    private fun resolveProfile(
        borderPreset: BorderPreset,
        borderWidthPx: Int,
        borderBlurRadiusPx: Int
    ): BorderRenderProfile {
        return when (borderPreset) {
            BorderPreset.SOLID -> BorderRenderProfile(
                sourceBlurRadius = 1,
                layers = listOf(
                    BorderLayerSpec(
                        radius = borderWidthPx,
                        blurRadius = 1,
                        alphaMultiplier = 1f
                    )
                )
            )
            BorderPreset.SOFT -> BorderRenderProfile(
                sourceBlurRadius = 2,
                layers = listOf(
                    BorderLayerSpec(
                        radius = borderWidthPx,
                        blurRadius = borderBlurRadiusPx.coerceIn(1, 24),
                        alphaMultiplier = 0.82f
                    )
                )
            )
            BorderPreset.DOUBLE -> BorderRenderProfile(
                sourceBlurRadius = 1,
                layers = listOf(
                    BorderLayerSpec(
                        radius = borderWidthPx,
                        blurRadius = 1,
                        alphaMultiplier = 0.62f
                    ),
                    BorderLayerSpec(
                        radius = max(1, borderWidthPx / 2),
                        blurRadius = 0,
                        alphaMultiplier = 1f
                    )
                )
            )
            BorderPreset.OUTLINE -> BorderRenderProfile(
                sourceBlurRadius = 1,
                layers = listOf(
                    BorderLayerSpec(
                        radius = borderWidthPx,
                        blurRadius = 0,
                        alphaMultiplier = 1f
                    )
                )
            )
        }
    }

    private suspend fun buildLayerMask(
        sourceAlpha: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        outWidth: Int,
        outHeight: Int,
        padding: Int,
        radius: Int
    ): IntArray {
        coroutineContext.ensureActive()

        val mask = IntArray(outWidth * outHeight)
        for (y in 0 until sourceHeight) {
            if ((y and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val srcRow = y * sourceWidth
            val dstRow = (y + padding) * outWidth + padding
            for (x in 0 until sourceWidth) {
                mask[dstRow + x] = sourceAlpha[srcRow + x]
            }
        }

        if (radius > 0) {
            dilate2DSeparable(mask, outWidth, outHeight, radius)
        }
        return mask
    }

    private suspend fun boxBlur(
        data: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        if (radius <= 0) return

        coroutineContext.ensureActive()
        val temp = IntArray(data.size)
        val horizontalPrefix = IntArray(width + 1)
        for (y in 0 until height) {
            if ((y and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val row = y * width
            horizontalPrefix[0] = 0
            for (x in 0 until width) {
                horizontalPrefix[x + 1] = horizontalPrefix[x] + data[row + x]
            }
            for (x in 0 until width) {
                val left = max(0, x - radius)
                val right = min(width - 1, x + radius)
                val sum = horizontalPrefix[right + 1] - horizontalPrefix[left]
                temp[row + x] = sum / (right - left + 1)
            }
        }

        val verticalPrefix = IntArray(height + 1)
        for (x in 0 until width) {
            if ((x and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            verticalPrefix[0] = 0
            for (y in 0 until height) {
                verticalPrefix[y + 1] = verticalPrefix[y] + temp[y * width + x]
            }
            for (y in 0 until height) {
                val top = max(0, y - radius)
                val bottom = min(height - 1, y + radius)
                val sum = verticalPrefix[bottom + 1] - verticalPrefix[top]
                data[y * width + x] = sum / (bottom - top + 1)
            }
        }
    }

    private suspend fun dilate2DSeparable(
        data: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        coroutineContext.ensureActive()

        val temp = IntArray(data.size)
        val r = radius.coerceIn(0, maxOf(width, height))
        for (y in 0 until height) {
            if ((y and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            dilate1DMax(data, temp, y * width, width, r)
        }
        for (x in 0 until width) {
            if ((x and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            dilate1DColumnMax(temp, data, x, width, height, r)
        }
    }

    private suspend fun dilate1DMax(
        src: IntArray,
        dst: IntArray,
        offset: Int,
        length: Int,
        radius: Int
    ) {
        val d = ArrayDeque<Int>()
        for (i in 0 until length) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val windowStart = (i - radius).coerceAtLeast(0)
            while (d.isNotEmpty() && d.first() < windowStart) {
                d.removeFirst()
            }
            val j = i + radius
            if (j < length) {
                val v = src[offset + j]
                while (d.isNotEmpty() && src[offset + d.last()] <= v) {
                    d.removeLast()
                }
                d.addLast(j)
            }
            dst[offset + i] = if (d.isEmpty()) 0 else src[offset + d.first()]
        }
    }

    private suspend fun dilate1DColumnMax(
        src: IntArray,
        dst: IntArray,
        col: Int,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val d = ArrayDeque<Int>()
        for (i in 0 until height) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val windowStart = (i - radius).coerceAtLeast(0)
            while (d.isNotEmpty() && d.first() < windowStart) {
                d.removeFirst()
            }
            val j = i + radius
            if (j < height) {
                val v = src[j * width + col]
                while (d.isNotEmpty() && src[d.last() * width + col] <= v) {
                    d.removeLast()
                }
                d.addLast(j)
            }
            val idx = i * width + col
            dst[idx] = if (d.isEmpty()) 0 else src[d.first() * width + col]
        }
    }

    private data class BorderRenderProfile(
        val sourceBlurRadius: Int,
        val layers: List<BorderLayerSpec>
    )

    private data class BorderLayerSpec(
        val radius: Int,
        val blurRadius: Int,
        val alphaMultiplier: Float
    )
}
