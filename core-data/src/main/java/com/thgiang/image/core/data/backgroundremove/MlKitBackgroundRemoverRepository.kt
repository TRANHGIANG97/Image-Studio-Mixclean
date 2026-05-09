package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Repository xóa phông sử dụng ML Kit Subject Segmentation + Guided Filter + smoothing alpha.
 * Viền đối tượng được làm mịn bằng Gaussian blur nhẹ (khử răng cưa) kết hợp Guided Filter
 * để bảo toàn chi tiết tóc và biên mảnh.
 */
class MlKitBackgroundRemoverRepository(
    private val context: Context,
    private val maxDecodeSize: Int = 2048,                // giới hạn kích thước ảnh decode
    private val createDisplayCopy: Boolean = true,        // có copy bitmap để hiển thị hay không
    private val alphaSmoothingRadius: Float = 1.5f        // bán kính blur cho alpha (pixel)
) : BackgroundRemoverRepository {

    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    // =============================== API công khai ===============================

    override suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput> = runCatching {
        val original = decodeBitmapWithResize(imageUri)
            ?: error("Cannot decode selected image (OOM or invalid image)")

        val foreground = runCatching {
            getForegroundBitmapInternal(original)
        }.getOrElse { e ->
            original.recycle()
            throw e
        }
        original.recycle()

        val display = if (createDisplayCopy) {
            foreground.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            foreground
        }

        BackgroundRemovalOutput(
            foregroundToDisplay = display,
            foregroundToSave = foreground
        )
    }

    override suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap> = runCatching {
        validateBitmap(bitmap)
        getForegroundBitmapInternal(bitmap)
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> =
        runCatching {
            validateBitmap(bitmap)
            val input = InputImage.fromBitmap(bitmap, 0)
            val result = withContext(Dispatchers.Default) {
                segmenter.process(input).await()
            }
            val buffer = result.foregroundConfidenceMask
                ?: error("Foreground confidence mask not available")
            buffer.rewind()
            val count = buffer.remaining()
            val values = FloatArray(count)
            buffer.get(values)
            val w = bitmap.width
            val h = bitmap.height

            when {
                count == w * h -> PortraitConfidenceMask(w, h, values)
                else -> {
                    val side = sqrt(count.toDouble()).toInt()
                    require(side * side == count) {
                        "Unexpected mask size $count for bitmap ${w}x$h. Only square masks are supported."
                    }
                    PortraitConfidenceMask(side, side, values)
                }
            }
        }

    override fun close() {
        runCatching { segmenter.close() }
    }

    // =============================== Xử lý nội bộ ===============================

    private suspend fun getForegroundBitmapInternal(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = segmenter.process(input).await()
        val foreground = result.foregroundBitmap?.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Foreground extraction failed")

        try {
            result.foregroundConfidenceMask?.let { mask ->
                applyConfidenceMaskToAlpha(foreground, mask, bitmap.width, bitmap.height, bitmap)
            }
            // Bước cuối: làm mịn alpha để khử răng cưa viền
            smoothAlphaEdges(foreground, alphaSmoothingRadius)
            foreground
        } catch (e: Exception) {
            foreground.recycle()
            throw e
        }
    }

    /**
     * Áp dụng confidence mask lên alpha channel, dùng Guided Filter để giữ chi tiết.
     * Nếu ảnh quá lớn (>2MP) sẽ downscale tạm thời để tăng tốc Guided Filter.
     */
    private fun applyConfidenceMaskToAlpha(
        foreground: Bitmap,
        mask: FloatBuffer,
        bitmapW: Int,
        bitmapH: Int,
        original: Bitmap
    ) {
        mask.rewind()
        val count = mask.remaining()
        val rawConfidences = FloatArray(count)
        mask.get(rawConfidences)

        val confidences = if (count == bitmapW * bitmapH) {
            rawConfidences
        } else {
            val side = sqrt(count.toDouble()).toInt()
            require(side * side == count) { "Mask size $count is not square and not equal to bitmap size" }
            upsampleConfidencesBilinear(rawConfidences, side, side, bitmapW, bitmapH)
        }

        // Guided Filter refinement (có thể downscale để tăng tốc)
        val refined = if (bitmapW * bitmapH > 2_000_000) { // > 2MP
            val scale = 0.5f
            val smallW = (bitmapW * scale).toInt()
            val smallH = (bitmapH * scale).toInt()
            val downsampledConf = downsampleConfidences(confidences, bitmapW, bitmapH, smallW, smallH)
            val smallOriginal = Bitmap.createScaledBitmap(original, smallW, smallH, true)
            val refinedSmall = GuidedFilter.refineAlpha(smallOriginal, downsampledConf, smallW, smallH)
            smallOriginal.recycle()
            upsampleConfidencesBilinear(refinedSmall, smallW, smallH, bitmapW, bitmapH)
        } else {
            GuidedFilter.refineAlpha(original, confidences, bitmapW, bitmapH)
        }

        // Ghi alpha vào foreground
        val fgW = foreground.width
        val fgH = foreground.height
        require(fgW == bitmapW && fgH == bitmapH) { "Foreground size mismatch" }

        val pixels = IntArray(fgW * fgH)
        foreground.getPixels(pixels, 0, fgW, 0, 0, fgW, fgH)

        for (i in pixels.indices) {
            val conf = refined[i].coerceIn(0f, 1f)
            val alpha = (conf * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (pixels[i] and 0x00FFFFFF) or (alpha shl 24)
        }

        foreground.setPixels(pixels, 0, fgW, 0, 0, fgW, fgH)
    }

    /**
     * Làm mịn alpha channel bằng Gaussian blur nhẹ, giúp viền đối tượng mềm mại,
     * khử hiện tượng răng cưa mà không làm mất nhiều chi tiết.
     * @param bitmap ảnh đầu vào (có alpha channel)
     * @param radius bán kính blur (pixel), càng lớn càng mờ viền
     */
    private fun smoothAlphaEdges(bitmap: Bitmap, radius: Float) {
        if (radius <= 0f) return
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Tách alpha channel thành mảng float
        val alpha = FloatArray(w * h)
        for (i in pixels.indices) {
            alpha[i] = (pixels[i] shr 24 and 0xFF) / 255f
        }

        // Gaussian blur nhẹ lên alpha
        val smoothed = gaussianBlur(alpha, w, h, radius)

        // Ghép lại
        for (i in pixels.indices) {
            val newAlpha = (smoothed[i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (pixels[i] and 0x00FFFFFF) or (newAlpha shl 24)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Gaussian blur 1D separable (hiệu năng tốt, O(N*kernelSize)).
     */
    private fun gaussianBlur(input: FloatArray, width: Int, height: Int, radius: Float): FloatArray {
        if (radius <= 0f) return input
        val sigma = radius / 2.0f  // heuristics
        val kernelSize = ceil(radius * 3).toInt().let { if (it % 2 == 0) it + 1 else it }
        val kernel = FloatArray(kernelSize)
        val center = kernelSize / 2
        var sum = 0f
        for (i in 0 until kernelSize) {
            val x = i - center
            kernel[i] = exp(-(x * x) / (2 * sigma * sigma))
            sum += kernel[i]
        }
        for (i in 0 until kernelSize) kernel[i] /= sum

        // Blur ngang
        val horizontal = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0f
                for (k in 0 until kernelSize) {
                    val sx = (x + k - center).coerceIn(0, width - 1)
                    acc += input[y * width + sx] * kernel[k]
                }
                horizontal[y * width + x] = acc
            }
        }

        // Blur dọc
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0f
                for (k in 0 until kernelSize) {
                    val sy = (y + k - center).coerceIn(0, height - 1)
                    acc += horizontal[sy * width + x] * kernel[k]
                }
                result[y * width + x] = acc
            }
        }
        return result
    }

    // =============================== Tiện ích xử lý mask ===============================

    private fun upsampleConfidencesBilinear(
        input: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        val output = FloatArray(dstW * dstH)
        val xScale = srcW.toFloat() / dstW
        val yScale = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            val srcY = y * yScale
            val y0 = srcY.toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceIn(0, srcH - 1)
            val dy = srcY - y0

            for (x in 0 until dstW) {
                val srcX = x * xScale
                val x0 = srcX.toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                val dx = srcX - x0

                val v00 = input[y0 * srcW + x0]
                val v01 = input[y1 * srcW + x0]
                val v10 = input[y0 * srcW + x1]
                val v11 = input[y1 * srcW + x1]

                val v0 = v00 * (1 - dx) + v10 * dx
                val v1 = v01 * (1 - dx) + v11 * dx
                output[y * dstW + x] = v0 * (1 - dy) + v1 * dy
            }
        }
        return output
    }

    private fun downsampleConfidences(
        input: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        val output = FloatArray(dstW * dstH)
        val xScale = srcW.toFloat() / dstW
        val yScale = srcH.toFloat() / dstH
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val srcX = (x * xScale).toInt().coerceIn(0, srcW - 1)
                val srcY = (y * yScale).toInt().coerceIn(0, srcH - 1)
                output[y * dstW + x] = input[srcY * srcW + srcX]
            }
        }
        return output
    }

    private fun validateBitmap(bitmap: Bitmap) {
        require(!bitmap.isRecycled) { "Bitmap is already recycled" }
        require(bitmap.config == Bitmap.Config.ARGB_8888) {
            "Bitmap config must be ARGB_8888, actual: ${bitmap.config}"
        }
    }

    private suspend fun decodeBitmapWithResize(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, this)
                }
                var sample = 1
                while (outWidth / sample > maxDecodeSize || outHeight / sample > maxDecodeSize) {
                    sample *= 2
                }
                inSampleSize = sample
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }

    // =============================== Guided Filter nội bộ ===============================
    // Triển khai đầy đủ thuật toán Guided Filter (He et al.) dành cho ảnh xám (alpha).
    // Độ phức tạp O(N) với N là số pixel, chấp nhận đánh đổi hiệu năng để có viền mịn.

    private object GuidedFilter {
        /**
         * Refine confidence mask (alpha) dùng ảnh gốc làm guidance.
         * @param guidance ảnh RGB hoặc grayscale (dùng luminance)
         * @param input alpha confidence ban đầu (float 0..1)
         * @param w chiều rộng
         * @param h chiều cao
         * @return mảng float refined alpha cùng kích thước
         */
        fun refineAlpha(guidance: Bitmap, input: FloatArray, w: Int, h: Int): FloatArray {
            // Chuyển ảnh guidance sang grayscale (luminance)
            val gray = if (guidance.config == Bitmap.Config.ARGB_8888) {
                getLuminance(guidance, w, h)
            } else {
                // Nếu ảnh không phải ARGB_8888, tạm dùng input làm guidance (fallback)
                input
            }

            val r = 16           // bán kính cửa sổ (có thể tinh chỉnh)
            val eps = 0.01f * 0.01f // regularization

            // 1. Mean của guidance và input
            val meanI = boxFilter(gray, w, h, r)
            val meanP = boxFilter(input, w, h, r)

            // 2. Phương sai và covariance
            val meanII = boxFilter(gray.map { it * it }.toFloatArray(), w, h, r)
            val varI = FloatArray(w * h)
            for (i in 0 until w * h) {
                varI[i] = meanII[i] - meanI[i] * meanI[i]
            }

            val meanIP = boxFilter(gray.zip(input).map { (g, p) -> g * p }.toFloatArray(), w, h, r)
            val covIP = FloatArray(w * h)
            for (i in 0 until w * h) {
                covIP[i] = meanIP[i] - meanI[i] * meanP[i]
            }

            // 3. Hệ số a, b
            val a = FloatArray(w * h)
            val b = FloatArray(w * h)
            for (i in 0 until w * h) {
                a[i] = covIP[i] / (varI[i] + eps)
                b[i] = meanP[i] - a[i] * meanI[i]
            }

            // 4. Mean của a, b
            val meanA = boxFilter(a, w, h, r)
            val meanB = boxFilter(b, w, h, r)

            // 5. Output = meanA * I + meanB
            return FloatArray(w * h).apply {
                for (i in 0 until w * h) {
                    this[i] = (meanA[i] * gray[i] + meanB[i]).coerceIn(0f, 1f)
                }
            }
        }

        private fun getLuminance(bitmap: Bitmap, w: Int, h: Int): FloatArray {
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            return FloatArray(w * h) { i ->
                val r = (pixels[i] shr 16 and 0xFF) / 255f
                val g = (pixels[i] shr 8 and 0xFF) / 255f
                val b = (pixels[i] and 0xFF) / 255f
                0.299f * r + 0.587f * g + 0.114f * b
            }
        }

        /**
         * Box filter (integral image) – O(N).
         */
        private fun boxFilter(input: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
            val output = FloatArray(w * h)
            val integral = FloatArray((w + 1) * (h + 1))
            // Tính integral image
            for (y in 0 until h) {
                var rowSum = 0f
                for (x in 0 until w) {
                    rowSum += input[y * w + x]
                    integral[(y + 1) * (w + 1) + (x + 1)] = rowSum + integral[y * (w + 1) + (x + 1)]
                }
            }
            // Lấy tổng vùng vuông
            val len = 2 * radius + 1
            for (y in 0 until h) {
                val y1 = max(0, y - radius)
                val y2 = min(h - 1, y + radius)
                for (x in 0 until w) {
                    val x1 = max(0, x - radius)
                    val x2 = min(w - 1, x + radius)
                    val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                            integral[(y1) * (w + 1) + (x2 + 1)] -
                            integral[(y2 + 1) * (w + 1) + (x1)] +
                            integral[y1 * (w + 1) + x1]
                    val area = (y2 - y1 + 1) * (x2 - x1 + 1)
                    output[y * w + x] = sum / area
                }
            }
            return output
        }
    }
}