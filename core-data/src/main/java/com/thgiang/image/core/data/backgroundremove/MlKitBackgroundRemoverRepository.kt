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
import kotlin.math.*

/**
 * Repository xóa phông sử dụng ML Kit Subject Segmentation.
 * Giữ ML Kit's alpha gốc (full resolution), chỉ thêm erosion + feathering + edge blur
 * để strip fringe + khử răng cưa viền mà không làm mờ interior.
 */
class MlKitBackgroundRemoverRepository(
    private val context: Context,
    private val maxDecodeSize: Int = 2048,                // giới hạn kích thước ảnh decode
    private val createDisplayCopy: Boolean = true,        // có copy bitmap để hiển thị hay không
    private val alphaSmoothingRadius: Float = 1.5f        // bán kính blur cho alpha (pixel)
) : BackgroundRemoverRepository {

    private companion object {
        private const val MIN_PRE_UPSCALE_PIXELS = 300_000
    }

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
        // Pre-upscale ảnh nhỏ để ML Kit có thêm pixel → mask mịn hơn → giảm răng cưa viền
        val pixels = bitmap.width * bitmap.height
        val needsUpscale = pixels < MIN_PRE_UPSCALE_PIXELS
        val processedBitmap = if (needsUpscale) {
            val scale = sqrt(MIN_PRE_UPSCALE_PIXELS.toFloat() / pixels)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(bitmap.width + 1),
                (bitmap.height * scale).toInt().coerceAtLeast(bitmap.height + 1),
                true
            )
        } else bitmap

        val input = InputImage.fromBitmap(processedBitmap, 0)
        val result = segmenter.process(input).await()
        val foreground = result.foregroundBitmap?.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Foreground extraction failed")

        try {
            // Native refinement: erosion + feathering + edge-aware blur (C++ JNI)
            BackgroundRefinerNative.nativeRefineForeground(foreground, processedBitmap)

            if (needsUpscale) {
                if (processedBitmap !== bitmap && !processedBitmap.isRecycled) processedBitmap.recycle()
                val scaledDown = Bitmap.createScaledBitmap(foreground, bitmap.width, bitmap.height, true)
                if (scaledDown !== foreground && !foreground.isRecycled) foreground.recycle()
                scaledDown
            } else foreground
        } catch (e: Exception) {
            foreground.recycle()
            throw e
        }
    }

    // =============================== Tiện ích ===============================

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

}