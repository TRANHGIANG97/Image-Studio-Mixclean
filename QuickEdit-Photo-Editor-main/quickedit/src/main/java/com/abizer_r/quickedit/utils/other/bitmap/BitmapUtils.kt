package com.abizer_r.quickedit.utils.other.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.abizer_r.quickedit.utils.AppUtils
import com.thgiang.image.core.util.MemoryUtil
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream


object BitmapUtils {

    fun getScaledBitmap(
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
        val maxSide = MemoryUtil.maxEditorBitmapSide(context)
        val inSampleSize = calculateInSampleSize(screenSize, bitmapSize, maxSide)

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
        maxSidePx: Int,
    ): Int {
        val (reqWidth, reqHeight) = screenSize
        val (width, height) = bitmapSize

        var inSampleSize = 1
        while (width / inSampleSize > maxSidePx ||
            height / inSampleSize > maxSidePx ||
            width / inSampleSize > reqWidth ||
            height / inSampleSize > reqHeight
        ) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Checks if the bitmap contains any pixels with alpha < 255.
     * Samples a grid of pixels to avoid allocating a full IntArray(w*h) (OOM risk).
     */
    fun hasTransparentPixels(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled) return false
        if (!bitmap.hasAlpha()) return false

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false

        // Sample ~64x64 grid across the image (covers edges + interior).
        val stepsX = minOf(64, width)
        val stepsY = minOf(64, height)
        for (sy in 0 until stepsY) {
            val y = (sy * (height - 1)) / (stepsY - 1).coerceAtLeast(1)
            for (sx in 0 until stepsX) {
                val x = (sx * (width - 1)) / (stepsX - 1).coerceAtLeast(1)
                if ((bitmap.getPixel(x, y) ushr 24) < 255) {
                    return true
                }
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
        return getDownSampledBitmap(context, uri, MemoryUtil.maxEditorBitmapSide(context))
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
