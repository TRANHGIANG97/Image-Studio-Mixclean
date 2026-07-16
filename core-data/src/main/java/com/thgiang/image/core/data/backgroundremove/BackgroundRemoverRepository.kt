package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import android.net.Uri

interface BackgroundRemoverRepository {
    suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput>
    suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap>
    /** ML Kit / magic-portrait style per-pixel subject confidence for blending. */
    suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask>
    fun consumeSelfieFallbackWarning(): Boolean
    /** True when Subject Segmentation needs a newer Google Play services install/update. */
    fun consumePlayServicesUpdateRecommended(): Boolean
    fun close()
}
