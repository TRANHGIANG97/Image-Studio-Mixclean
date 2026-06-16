package com.thgiang.image.core.data.backgroundremove

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer

object SelfieFallbackSegmenter {
    private val selfieSegmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    suspend fun process(input: InputImage): ByteBuffer {
        return selfieSegmenter.process(input).await().buffer
    }

    fun close() {
        selfieSegmenter.close()
    }
    
    val isSupported: Boolean = true
}
