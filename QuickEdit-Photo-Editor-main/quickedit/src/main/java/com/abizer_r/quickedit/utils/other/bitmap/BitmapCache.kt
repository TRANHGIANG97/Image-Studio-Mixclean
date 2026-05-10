package com.abizer_r.quickedit.utils.other.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

class BitmapCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "bitmap_cache")

    init {
        cacheDir.mkdirs()
    }

    /**
     * Saves bitmap to disk cache as PNG (lossless, supports alpha).
     * @return cache ID string, or null on failure
     */
    fun saveBitmap(bitmap: Bitmap, useJpeg: Boolean = false): String? {
        return try {
            val id = UUID.randomUUID().toString()
            val ext = if (useJpeg) "jpg" else "png"
            val file = File(cacheDir, "$id.$ext")
            file.outputStream().use { out ->
                if (useJpeg) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads bitmap from disk cache by ID.
     * @return the loaded Bitmap, or null if not found
     */
    fun loadBitmap(id: String): Bitmap? {
        return try {
            // Check both extensions
            var file = File(cacheDir, "$id.png")
            if (!file.exists()) file = File(cacheDir, "$id.jpg")
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes cached bitmap file by ID.
     */
    fun deleteBitmap(id: String) {
        val filePng = File(cacheDir, "$id.png")
        if (filePng.exists()) filePng.delete()
        val fileJpg = File(cacheDir, "$id.jpg")
        if (fileJpg.exists()) fileJpg.delete()
    }

    /**
     * Clears all cached bitmaps from disk.
     */
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
