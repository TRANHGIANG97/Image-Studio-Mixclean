package com.thgiang.image.core.util.processors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.MemoryUtil
import com.thgiang.image.core.util.processors.ProcessorUtils.compositeForegroundOverBackground
import com.thgiang.image.core.util.processors.ProcessorUtils.toArgbBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CleanProcessor {
    private const val TAG = "CleanProcessor"

    suspend fun applyClean(
        context: Context,
        bitmap: Bitmap,
        intensity: Float,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val clamped = intensity.coerceIn(0f, 1f)
            if (clamped <= 0f) return@runCatching source

            val repo = backgroundRemoverRepository
            val foreground = repo.getForegroundBitmap(source).getOrNull()
                ?: run {
                    if (!source.isRecycled) source.recycle()
                    return@runCatching null
                }

            if (!MemoryUtil.isBitmapSizeFeasible(source.width, source.height, context)) {
                Log.w(TAG, "Clean: bitmap too large for device heap, skipping")
                if (!source.isRecycled) source.recycle()
                return@runCatching foreground
            }

            val backgroundEnhanced = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(backgroundEnhanced)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val contrast = 1f + (clamped * 0.5f)
            val brightness = clamped * 10f
            val saturation = 1f + (clamped * 0.5f)

            val cm = ColorMatrix()
            cm.setSaturation(saturation)
            val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(contrastMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            if (!source.isRecycled) source.recycle()

            val output = compositeForegroundOverBackground(backgroundEnhanced, foreground)
            if (!backgroundEnhanced.isRecycled) backgroundEnhanced.recycle()
            if (!foreground.isRecycled) foreground.recycle()
            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applyCleanCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        intensity: Float
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val clamped = intensity.coerceIn(0f, 1f)
            if (clamped <= 0f) {
                if (!source.isRecycled) source.recycle()
                return@runCatching compositeForegroundOverBackground(source, foreground)
            }

            val backgroundEnhanced = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(backgroundEnhanced)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val contrast = 1f + (clamped * 0.5f)
            val brightness = clamped * 10f
            val saturation = 1f + (clamped * 0.5f)

            val cm = ColorMatrix()
            cm.setSaturation(saturation)
            val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(contrastMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
            if (!source.isRecycled) source.recycle()

            val output = compositeForegroundOverBackground(backgroundEnhanced, foreground)
            if (!backgroundEnhanced.isRecycled) backgroundEnhanced.recycle()
            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }
}
