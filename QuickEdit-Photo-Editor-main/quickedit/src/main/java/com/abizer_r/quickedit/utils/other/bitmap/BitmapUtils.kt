package com.abizer_r.quickedit.utils.other.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.abizer_r.quickedit.utils.AppUtils
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream


object BitmapUtils {

    suspend fun getScaledBitmap(
        context: Context,
        uri: Uri
    ) = flow<BitmapStatus> {
        emit(BitmapStatus.Processing)
        val scaledBitmap = decodeSampledBitmapFromResource(context, uri)
        if (scaledBitmap != null) {
            emit(BitmapStatus.Success(scaledBitmap))
        } else {
            emit(BitmapStatus.Failed())
        }
    }

    private fun decodeSampledBitmapFromResource(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        val bitmapSize = run {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            Pair(opts.outWidth, opts.outHeight)
        }

        val screenSize = AppUtils.getScreenWidthAndHeight(context)
        val inSampleSize = calculateInSampleSize(screenSize, bitmapSize)

        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }

        val bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        // Apply EXIF orientation — front-camera photos store pixel data in landscape
        // but include a rotation tag. Without this, images appear sideways.
        val rotation = getExifRotation(context, uri)
        return if (rotation != 0 && bitmap != null) {
            rotateBitmap(bitmap, rotation)
        } else {
            bitmap
        }
    }

    /** Read EXIF orientation from URI and return clockwise rotation in degrees. */
    private fun getExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** Rotate bitmap by degrees, recycling the original. */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }

    private fun calculateInSampleSize(
        screenSize: Pair<Int, Int>,
        bitmapSize: Pair<Int, Int>,
    ): Int {
        // Raw height and width of image
        val (reqWidth, reqHeight) = screenSize
        val (width, height) = bitmapSize

        var inSampleSize = 0

        do {
            inSampleSize++
            val compressedWidth = width / inSampleSize
            val compressedHeight = height / inSampleSize

        } while (compressedWidth > reqWidth && compressedHeight > reqHeight)

        return inSampleSize
    }

    /**
     * Checks if the bitmap contains any pixels with alpha < 255.
     * Uses a sampled approach for large bitmaps to maintain performance.
     */
    fun hasTransparentPixels(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        if (!bitmap.hasAlpha()) return false

        val width = bitmap.width
        val height = bitmap.height
        
        // For very large bitmaps, checking every pixel might be slow.
        // But for most mobile images, it's acceptable. 
        // We'll check in blocks or use a subset if needed, but let's start with full check for accuracy.
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (pixel in pixels) {
            if ((pixel ushr 24) < 255) {
                return true
            }
        }
        return false
    }

    fun saveBitmap(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
        }
    }

    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            // Apply EXIF orientation for consistency
            val rotation = getExifRotation(context, uri)
            if (rotation != 0 && bitmap != null) {
                rotateBitmap(bitmap, rotation)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode a bitmap from URI, applying EXIF orientation, with down-sampling
     * to keep the larger dimension within [maxPx].
     */
    fun getDownSampledBitmap(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        return try {
            // Read bounds
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val w = boundsOpts.outWidth
            val h = boundsOpts.outHeight
            if (w <= 0 || h <= 0) return null

            var sample = 1
            while (w / sample > maxPx || h / sample > maxPx) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }

            val rotation = getExifRotation(context, uri)
            if (rotation != 0 && bitmap != null) {
                rotateBitmap(bitmap, rotation)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
