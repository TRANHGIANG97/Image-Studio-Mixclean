package com.abizer_r.quickedit.utils.other

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class QuickToolsPortraitClassifier {
    suspend fun hasDetectableFace(bitmap: Bitmap): Result<Boolean> = runCatching {
        require(!bitmap.isRecycled) { "Bitmap is already recycled" }

        val analysisBitmap = bitmap.scaledForFaceDetection()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.05f)
                .build()
        )

        try {
            val faces = detector.process(InputImage.fromBitmap(analysisBitmap, 0)).await()
            faces.any { face ->
                val bounds = face.boundingBox
                val minSide = max(analysisBitmap.width, analysisBitmap.height) * 0.035f
                bounds.width() >= minSide && bounds.height() >= minSide
            }
        } finally {
            detector.close()
            if (analysisBitmap !== bitmap && !analysisBitmap.isRecycled) {
                analysisBitmap.recycle()
            }
        }
    }

    private fun Bitmap.scaledForFaceDetection(maxSide: Int = 1280): Bitmap {
        val currentMaxSide = max(width, height)
        if (currentMaxSide <= maxSide) return this
        val scale = maxSide.toFloat() / currentMaxSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}
