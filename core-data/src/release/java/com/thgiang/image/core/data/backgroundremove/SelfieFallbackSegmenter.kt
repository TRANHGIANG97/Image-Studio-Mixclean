package com.thgiang.image.core.data.backgroundremove

import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer

object SelfieFallbackSegmenter {
    suspend fun process(input: InputImage): ByteBuffer {
        throw UnsupportedOperationException("Selfie segmentation is only available in debug mode")
    }

    fun close() {
    }
    
    val isSupported: Boolean = false
}
