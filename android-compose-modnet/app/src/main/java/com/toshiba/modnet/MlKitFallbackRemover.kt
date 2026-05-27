package com.toshiba.modnet

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

class MlKitFallbackRemover(
    private val context: Context,
) : AutoCloseable {
    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        SubjectSegmentation.getClient(options)
    }

    suspend fun getMask(bitmap: Bitmap): Mask? {
        return runCatching {
            val source = bitmap.ensureArgb8888CopyIfNeeded()
            val buffer = process(InputImage.fromBitmap(source, 0)).foregroundConfidenceMask
                ?: return null
            readMask(buffer, source.width, source.height)
        }.getOrNull()
    }

    override fun close() {
        runCatching { segmenter.close() }
    }

    private suspend fun process(image: InputImage) = suspendCancellableCoroutine { continuation ->
        segmenter.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    private fun readMask(buffer: FloatBuffer, bitmapWidth: Int, bitmapHeight: Int): Mask {
        buffer.rewind()
        val count = buffer.remaining()
        val values = FloatArray(count)
        buffer.get(values)
        return if (count == bitmapWidth * bitmapHeight) {
            Mask(bitmapWidth, bitmapHeight, values)
        } else {
            val side = sqrt(count.toDouble()).toInt()
            require(side * side == count) { "Unexpected ML Kit mask size: $count" }
            Mask(side, side, values).resizeBilinear(bitmapWidth, bitmapHeight)
        }
    }
}
