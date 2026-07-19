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

    private val nativeAvailable: Boolean = try {
        System.loadLibrary("portrait_processor")
        true
    } catch (error: Throwable) {
        // System.loadLibrary throws UnsatisfiedLinkError (an Error, not Exception).
        // Keep portrait effects optional instead of crashing during object initialization.
        android.util.Log.e(TAG, "Failed to load portrait_processor library", error)
        false
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
        if (!nativeAvailable) return@withContext null
        val job = coroutineContext[Job]
        var source: Bitmap? = null
        runCatching {
            source = bitmap.toArgbBitmap() ?: return@runCatching null

            if (job?.isActive != true) return@runCatching null

            val output = nativeApplyBlur(
                srcBitmap = source!!,
                blurRadius = blurRadius.coerceIn(0f, 25f)
            )
            output
        }.also {
            source?.let { owned -> if (!owned.isRecycled) owned.recycle() }
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applySubjectBlur(
        bitmap: Bitmap,
        blurRadius: Float
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!nativeAvailable) return@withContext null
        val job = coroutineContext[Job]
        var source: Bitmap? = null
        runCatching {
            source = bitmap.toArgbBitmap() ?: return@runCatching null

            if (job?.isActive != true) return@runCatching null

            val output = nativeApplySubjectBlur(
                srcBitmap = source!!,
                blurRadius = blurRadius.coerceIn(0f, 25f)
            )
            output
        }.also {
            source?.let { owned -> if (!owned.isRecycled) owned.recycle() }
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
        if (!nativeAvailable) return@withContext null
        val job = coroutineContext[Job]
        var source: Bitmap? = null
        var foreground: Bitmap? = null
        runCatching {
            source = bitmap.toArgbBitmap() ?: return@runCatching null
            val repo = backgroundRemoverRepository
            foreground = repo.getForegroundBitmap(source!!).getOrNull()

            if (job?.isActive != true) return@runCatching null

            // nativeApplyPortrait handles null foreground internally (does not composite)
            val output = nativeApplyPortrait(
                srcBitmap = source!!,
                fgBitmap = foreground,
                blurRadius = blurRadius.coerceIn(0f, 25f),
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            output
        }.also {
            source?.let { owned -> if (!owned.isRecycled) owned.recycle() }
            foreground?.let { owned -> if (!owned.isRecycled) owned.recycle() }
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applyPortraitCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!nativeAvailable) return@withContext null
        val job = coroutineContext[Job]
        var source: Bitmap? = null
        var ownedForeground: Bitmap? = null
        runCatching {
            source = bitmap.toArgbBitmap() ?: return@runCatching null

            if (job?.isActive != true) return@runCatching null

            val fgReady = if (foreground.config == Bitmap.Config.ARGB_8888) {
                foreground
            } else {
                foreground.toArgbBitmap()?.also { ownedForeground = it }
                    ?: return@runCatching null
            }

            val output = nativeApplyPortrait(
                srcBitmap = source!!,
                fgBitmap = fgReady,
                blurRadius = blurRadius.coerceIn(0f, 25f),
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            output
        }.also {
            source?.let { owned -> if (!owned.isRecycled) owned.recycle() }
            ownedForeground?.let { owned -> if (!owned.isRecycled) owned.recycle() }
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }
}

