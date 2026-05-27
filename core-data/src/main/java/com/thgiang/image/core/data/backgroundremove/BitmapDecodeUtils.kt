package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object BitmapDecodeUtils {
    private const val TAG = "BitmapDecodeUtils"

    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDecodeSize: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    val scale = calculateDecodeScale(width, height, maxDecodeSize)
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }.ensureSoftwareArgb8888()
            } else {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }

                var sample = 1
                while (bounds.outWidth / sample > maxDecodeSize || bounds.outHeight / sample > maxDecodeSize) {
                    sample *= 2
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching null

                // Handle EXIF orientation for older devices
                val rotationDegrees = getExifRotationDegrees(context, uri)
                val rotatedBitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    if (rotated !== bitmap && !bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    rotated
                } else {
                    bitmap
                }

                rotatedBitmap.ensureSoftwareArgb8888()
            }
        }.onFailure {
            Log.e(TAG, "Failed to load bitmap from uri: $uri", it)
        }.getOrNull()
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF for $uri", e)
            0
        }
    }

    private fun calculateDecodeScale(width: Int, height: Int, maxSide: Int): Float {
        val srcMax = maxOf(width, height)
        return if (srcMax <= maxSide) 1f else maxSide.toFloat() / srcMax
    }

    private fun Bitmap.ensureSoftwareArgb8888(): Bitmap {
        require(!isRecycled) { "Bitmap is recycled" }
        return if (config == Bitmap.Config.ARGB_8888) {
            this
        } else {
            copy(Bitmap.Config.ARGB_8888, true).also {
                it.setHasAlpha(true)
            }
        }
    }
}
