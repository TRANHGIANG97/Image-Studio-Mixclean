package com.thgiang.image.studio.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.studio.util.assetCacheKey
import com.thgiang.image.studio.util.openAssetSourceInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleObjectCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backgroundRemoverRepository: BackgroundRemoverRepository
) {
    /** Return cached foreground URI if available, otherwise extract and cache it. */
    suspend fun getOrExtract(assetPath: String): Uri? = withContext(Dispatchers.IO) {
        val cacheKey = assetCacheKey(assetPath) + "_extracted.png"
        val cacheFile = File(context.cacheDir, cacheKey)

        if (cacheFile.exists()) {
            return@withContext Uri.fromFile(cacheFile)
        }

        try {
            val input = context.openAssetSourceInputStream(assetPath) ?: return@withContext null
            val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext null

            val fg = backgroundRemoverRepository.getForegroundBitmap(bitmap).getOrNull()
                ?: return@withContext null

            cacheFile.outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fg.recycle()
            bitmap.recycle()

            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            android.util.Log.e("SampleObjectCache", "Failed to extract sample object for $assetPath", e)
            null
        }
    }
}
