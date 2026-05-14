package com.thgiang.image.core.util.processors

import android.content.Context
import android.graphics.Bitmap
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.blurBitmapForPortraitExport
import com.thgiang.image.core.util.processors.ProcessorUtils.compositeForegroundOverBackground
import com.thgiang.image.core.util.processors.ProcessorUtils.toArgbBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

object PortraitProcessor {
    private const val TAG = "PortraitProcessor"

    init {
        try {
            System.loadLibrary("portrait_processor")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load portrait_processor library", e)
        }
    }

    private external fun nativeApplyPortrait(
        srcBitmap: Bitmap,
        fgBitmap: Bitmap?,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean
    ): Bitmap?

    private external fun nativeApplyBlur(
        srcBitmap: Bitmap,
        blurRadius: Float
    ): Bitmap?

    private external fun nativeApplySubjectBlur(
        srcBitmap: Bitmap,
        blurRadius: Float
    ): Bitmap?

    suspend fun applyBlurOnly(
        bitmap: Bitmap,
        blurRadius: Float
    ): Bitmap? = withContext(Dispatchers.Default) {
        val job = coroutineContext[Job]
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null

            if (job?.isActive != true) return@runCatching null

            val output = nativeApplyBlur(
                srcBitmap = source,
                blurRadius = blurRadius.coerceIn(0f, 25f)
            )

            if (!source.isRecycled) source.recycle()

            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applySubjectBlur(
        bitmap: Bitmap,
        blurRadius: Float
    ): Bitmap? = withContext(Dispatchers.Default) {
        val job = coroutineContext[Job]
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null

            if (job?.isActive != true) return@runCatching null

            val output = nativeApplySubjectBlur(
                srcBitmap = source,
                blurRadius = blurRadius.coerceIn(0f, 25f)
            )

            if (!source.isRecycled) source.recycle()

            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applyPortrait(
        context: Context,
        bitmap: Bitmap,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = withContext(Dispatchers.Default) {
        val job = coroutineContext[Job]
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val repo = backgroundRemoverRepository
            val foreground = repo.getForegroundBitmap(source).getOrNull()

            if (job?.isActive != true) return@runCatching null

            // nativeApplyPortrait handles null foreground internally (does not composite)
            val output = nativeApplyPortrait(
                srcBitmap = source,
                fgBitmap = foreground,
                blurRadius = blurRadius.coerceIn(0f, 25f),
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            
            if (!source.isRecycled) source.recycle()
            foreground?.let { if (!it.isRecycled) it.recycle() }
            
            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applyPortraitCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean
    ): Bitmap? = withContext(Dispatchers.Default) {
        val job = coroutineContext[Job]
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null

            if (job?.isActive != true) return@runCatching null

            val fgReady = if (foreground.config == Bitmap.Config.ARGB_8888) {
                foreground
            } else {
                foreground.toArgbBitmap() ?: return@runCatching null
            }

            val output = nativeApplyPortrait(
                srcBitmap = source,
                fgBitmap = fgReady,
                blurRadius = blurRadius.coerceIn(0f, 25f),
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )

            if (!source.isRecycled) source.recycle()
            if (fgReady !== foreground && !fgReady.isRecycled) fgReady.recycle()
            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }
}

