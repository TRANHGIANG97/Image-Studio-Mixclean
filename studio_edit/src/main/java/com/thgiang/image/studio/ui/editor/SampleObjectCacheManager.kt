package com.thgiang.image.studio.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
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
    /** Trả về URI ảnh đã tách nền. Nếu đã cache thì dùng cache, nếu chưa thì tách và lưu. */
    suspend fun getOrExtract(assetPath: String): Uri? = withContext(Dispatchers.IO) {
        val cacheKey = assetPath.replace("/", "_") + "_extracted.png"
        val cacheFile = File(context.cacheDir, cacheKey)
        
        if (cacheFile.exists()) {
            return@withContext Uri.fromFile(cacheFile)
        }
        
        try {
            // Lần đầu: tách nền và lưu vào cache
            val bitmap = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } 
                ?: return@withContext null
            
            // Xử lý tách nền bằng ML Kit
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
