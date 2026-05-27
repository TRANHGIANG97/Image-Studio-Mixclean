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

    fun fastGuidedFilter(guidanceImage: Mask, radius: Int, eps: Float, downsampleFactor: Int = 4): Mask {
        val sW = max(1, width / downsampleFactor)
        val sH = max(1, height / downsampleFactor)

        val pSubRaw = this.data.resizeBilinearRaw(width, height, sW, sH)
        // Nở rộng (dilate) mặt nạ thô 2 pixel ở không gian thu nhỏ (tương đương 8 pixel ở ảnh gốc)
        // để mở rộng vùng tìm kiếm cho Guided Filter đối với các sợi tóc tơ bay tự do ngoài biên
        val pSubData = pSubRaw.dilateRaw(sW, sH, radius = 2)
        val ISubData = guidanceImage.data.resizeBilinearRaw(width, height, sW, sH)

        val mean_I = ISubData.boxBlurRaw(sW, sH, radius)
        val mean_p = pSubData.boxBlurRaw(sW, sH, radius)

        val II = FloatArray(sW * sH) { i -> ISubData[i] * ISubData[i] }
        val Ip = FloatArray(sW * sH) { i -> ISubData[i] * pSubData[i] }

        val mean_II = II.boxBlurRaw(sW, sH, radius)
        val mean_Ip = Ip.boxBlurRaw(sW, sH, radius)

        val aData = FloatArray(sW * sH)
        val bData = FloatArray(sW * sH)
        for (i in aData.indices) {
            val var_I = mean_II[i] - mean_I[i] * mean_I[i]
            val cov_Ip = mean_Ip[i] - mean_I[i] * mean_p[i]
            aData[i] = cov_Ip / (var_I + eps)
            bData[i] = mean_p[i] - aData[i] * mean_I[i]
        }

        val mean_a = aData.boxBlurRaw(sW, sH, radius)
        val mean_b = bData.boxBlurRaw(sW, sH, radius)

        val mean_a_full = mean_a.resizeBilinearRaw(sW, sH, width, height)
        val mean_b_full = mean_b.resizeBilinearRaw(sW, sH, width, height)

        val qData = FloatArray(width * height)
        for (i in qData.indices) {
            qData[i] = (mean_a_full[i] * guidanceImage.data[i] + mean_b_full[i]).coerceIn(0f, 1f)
        }
        return Mask(width, height, qData)
    }
}

private fun FloatArray.dilateRaw(width: Int, height: Int, radius: Int): FloatArray {
    if (radius <= 0) return this.clone()
    val out = FloatArray(width * height)
    val temp = FloatArray(width * height)

    // Co giãn theo chiều ngang (Horizontal pass)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var maxVal = 0f
            val start = maxOf(0, x - radius)
            val end = minOf(width - 1, x + radius)
            for (kx in start..end) {
                val v = this[row + kx]
                if (v > maxVal) maxVal = v
            }
            temp[row + x] = maxVal
        }
    }

    // Co giãn theo chiều dọc (Vertical pass)
    for (x in 0 until width) {
        for (y in 0 until height) {
            var maxVal = 0f
            val start = maxOf(0, y - radius)
            val end = minOf(height - 1, y + radius)
            for (ky in start..end) {
                val v = temp[ky * width + x]
                if (v > maxVal) maxVal = v
            }
            out[y * width + x] = maxVal
        }
    }

    return out
}


private fun FloatArray.boxBlurRaw(width: Int, height: Int, radius: Int): FloatArray {
    if (radius <= 0) return this.clone()

    val temp = FloatArray(width * height)
    val out = FloatArray(width * height)
    val kernel = radius * 2 + 1

    for (y in 0 until height) {
        val row = y * width
        var sum = 0f
        for (kx in -radius..radius) {
            sum += this[row + kx.coerceIn(0, width - 1)]
        }
        for (x in 0 until width) {
            temp[row + x] = sum / kernel
            val removeX = (x - radius).coerceIn(0, width - 1)
            val addX = (x + radius + 1).coerceIn(0, width - 1)
            sum += this[row + addX] - this[row + removeX]
        }
    }

    for (x in 0 until width) {
        var sum = 0f
        for (ky in -radius..radius) {
            sum += temp[ky.coerceIn(0, height - 1) * width + x]
        }
        for (y in 0 until height) {
            out[y * width + x] = sum / kernel
            val removeY = (y - radius).coerceIn(0, height - 1)
            val addY = (y + radius + 1).coerceIn(0, height - 1)
            sum += temp[addY * width + x] - temp[removeY * width + x]
        }
    }

    return out
}

private fun FloatArray.resizeBilinearRaw(width: Int, height: Int, targetWidth: Int, targetHeight: Int): FloatArray {
    if (targetWidth == width && targetHeight == height) return this.clone()

    val out = FloatArray(targetWidth * targetHeight)
    if (width <= 1 || height <= 1 || targetWidth <= 1 || targetHeight <= 1) {
        out.fill(this.firstOrNull() ?: 0f)
        return out
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
            val top = this[y0 * width + x0] * (1f - wx) + this[y0 * width + x1] * wx
            val bottom = this[y1 * width + x0] * (1f - wx) + this[y1 * width + x1] * wx
            out[dstRow + tx] = top * (1f - wy) + bottom * wy
        }
    }
    return out
}


fun bitmapToGrayscaleMask(bitmap: Bitmap): Mask {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val out = FloatArray(width * height)
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        out[i] = lum
    }
    return Mask(width, height, out)
}

fun applyMaskToImage(image: Bitmap, mask: Mask): Bitmap {
    require(mask.width == image.width && mask.height == image.height) {
        "Mask size ${mask.width}x${mask.height} != image ${image.width}x${image.height}"
    }

    val width = image.width
    val height = image.height
    val srcPixels = IntArray(width * height)
    image.getPixels(srcPixels, 0, width, 0, 0, width, height)

    // Áp dụng Fast Guided Filter để nắn viền mask bám sát theo chi tiết ảnh
    val guidanceImage = bitmapToGrayscaleMask(image)
    val refinedMask = mask.fastGuidedFilter(guidanceImage, radius = 4, eps = 0.001f, downsampleFactor = 4)

    var greenRgbDecontamPixels = 0
    var transparentGreenPixels = 0
    val pixelCount = srcPixels.size

    val outPixels = IntArray(pixelCount)
    for (i in srcPixels.indices) {
        val src = srcPixels[i]
        val srcAlpha = (src ushr 24) and 0xFF
        val maskFloat = refinedMask.data[i].coerceIn(0f, 1f)

        // Không dùng smoothstep/boost nữa vì Guided Filter đã lo phần sắc độ nét/nhòe
        val smoothedMask = maskFloat


        val maskAlpha = (smoothedMask * 255f).roundToInt()

        var alpha = (srcAlpha * maskAlpha / 255f).roundToInt().coerceIn(0, 255)

        val rOrig = (src shr 16) and 0xFF
        val gOrig = (src shr 8) and 0xFF
        val bOrig = src and 0xFF

        val isGreenSpill = gOrig > rOrig + 12 && gOrig > bOrig + 8 && gOrig > 65
        val isYellowGreenSpill = gOrig > bOrig + 8 && rOrig > bOrig + 8 &&
            kotlin.math.abs(rOrig - gOrig) < 65 && gOrig > 70

        // Xác định edge pixel: dùng maskFloat đã tính ở bước smoothstep phía trên
        val isEdgePixel = maskFloat in 0.03f..0.85f


        var r = rOrig
        var g = gOrig
        var b = bOrig

        if (isGreenSpill || isYellowGreenSpill) {
            if (isEdgePixel && alpha < 245) {
                // Green RGB decontamination: giảm green channel, không chỉ alpha
                g = minOf(g, maxOf(r, b) + 8)
                greenRgbDecontamPixels++

                if (alpha in 10..90) {
                    alpha = (alpha * 0.65f).roundToInt().coerceIn(0, 255)
                }
                if (alpha < 35 && (isGreenSpill || isYellowGreenSpill)) {
                    alpha = 0
                    transparentGreenPixels++
                }
            } else if (!isEdgePixel && alpha < 245) {
                // Non-edge green spill: suppress green but don't touch alpha (inner region)
                g = minOf(g, maxOf(r, b) + 16)
            }
        }

        // White fringe decontamination: chỉ áp dụng cho pixel KHÔNG có green spill
        // và KHÔNG ở chế độ green-suppress. Với green spill, công thức white decontam
        // (C - white)/a KHUẾCH ĐẠI green (do pixel xanh bị trừ white rồi chia a nhỏ).
        if (alpha in 10..235 && !isGreenSpill && !isYellowGreenSpill &&
            rOrig > 100 && gOrig > 100 && bOrig > 100) {
            val a = alpha / 255f
            // Giả định nền trắng (255,255,255) bị pha vào — chỉ khi pixel sáng
            r = ((rOrig - (1f - a) * 255f) / a).roundToInt().coerceIn(0, 255)
            g = ((gOrig - (1f - a) * 255f) / a).roundToInt().coerceIn(0, 255)
            b = ((bOrig - (1f - a) * 255f) / a).roundToInt().coerceIn(0, 255)
        }

        outPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    if (greenRgbDecontamPixels > 0) {
        android.util.Log.d(
            "MaskApply",
            "greenRgbDecontamPixels=${greenRgbDecontamPixels.toFloat() / pixelCount.toFloat()}, " +
            "transparentGreenPixels=${transparentGreenPixels.toFloat() / pixelCount.toFloat()}"
        )
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
