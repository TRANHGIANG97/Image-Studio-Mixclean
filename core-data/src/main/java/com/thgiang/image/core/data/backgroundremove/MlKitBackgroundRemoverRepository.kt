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
import kotlin.math.sqrt

class MlKitBackgroundRemoverRepository(
    private val context: Context
) : BackgroundRemoverRepository {

    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    override suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput> = runCatching {
        val original = decodeBitmapFromUri(imageUri)
            ?: error("Cannot decode selected image")

        val foreground = getForegroundBitmap(original)
            .getOrElse {
                original.recycle()
                throw it
            }
        original.recycle()

        val display = foreground.copy(Bitmap.Config.ARGB_8888, true)
        BackgroundRemovalOutput(
            foregroundToDisplay = display,
            foregroundToSave = foreground
        )
    }

    override suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap> = runCatching {
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = withContext(Dispatchers.Default) {
            segmenter.process(input).await()
        }
        val foreground = result.foregroundBitmap?.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Foreground extraction failed")

        // Dùng confidence mask để tạo alpha mềm ở biên chủ thể,
        // kết hợp Guided Filter để bảo toàn chi tiết tóc và biên mảnh
        result.foregroundConfidenceMask?.let { mask ->
            applyConfidenceMaskToAlpha(foreground, mask, bitmap.width, bitmap.height, bitmap)
        }

        foreground
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> =
        runCatching {
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
                    val side = kotlin.math.sqrt(count.toDouble()).toInt()
                    require(side * side == count) { "Unexpected mask size $count for bitmap ${w}x$h" }
                    PortraitConfidenceMask(side, side, values)
                }
            }
        }

    /**
     * Thay thế alpha channel của foreground bằng confidence mask từ ML Kit,
     * kết hợp Guided Filter để bảo toàn chi tiết tóc / biên mảnh.
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

        // Full-size confidence array (upsample nếu mask là square)
        val confidences = if (count == bitmapW * bitmapH) {
            rawConfidences
        } else {
            val side = sqrt(count.toDouble()).toInt()
            if (side * side == count) {
                upsampleConfidences(rawConfidences, side, side, bitmapW, bitmapH)
            } else {
                rawConfidences
            }
        }

        // Guided Filter refinement: dùng ảnh gốc làm guidance,
        // phục hồi chi tiết tóc và biên mảnh từ luminance của ảnh gốc
        val refined = GuidedFilter.refineAlpha(
            bitmap = original,
            confidence = confidences,
            w = bitmapW,
            h = bitmapH
        )

        val fgW = foreground.width
        val fgH = foreground.height
        val pixels = IntArray(fgW * fgH)
        foreground.getPixels(pixels, 0, fgW, 0, 0, fgW, fgH)

        for (i in pixels.indices) {
            val conf = refined[i].coerceIn(0f, 1f)
            val alpha = (conf * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (pixels[i] and 0x00FFFFFF) or (alpha shl 24)
        }

        foreground.setPixels(pixels, 0, fgW, 0, 0, fgW, fgH)
    }

    private fun upsampleConfidences(
        input: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        val output = FloatArray(dstW * dstH)
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val sx = (x.toFloat() / dstW * srcW).toInt().coerceIn(0, srcW - 1)
                val sy = (y.toFloat() / dstH * srcH).toInt().coerceIn(0, srcH - 1)
                output[y * dstW + x] = input[sy * srcW + sx]
            }
        }
        return output
    }

    override fun close() {
        runCatching { segmenter.close() }
    }

    private suspend fun decodeBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.copy(Bitmap.Config.ARGB_8888, true)
            }
        }.getOrNull()
    }
}