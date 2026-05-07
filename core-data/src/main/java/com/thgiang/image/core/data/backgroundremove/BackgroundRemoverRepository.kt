package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import android.net.Uri

interface BackgroundRemoverRepository {
    suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput>
    suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap>
    /** ML Kit / magic-portrait style per-pixel subject confidence for blending. */
    suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask>
    fun close()
}
