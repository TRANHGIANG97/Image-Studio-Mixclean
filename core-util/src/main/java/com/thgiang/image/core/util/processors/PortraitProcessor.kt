package com.thgiang.image.core.util.processors

import android.content.Context
import android.graphics.Bitmap
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.blurBitmapForPortraitExport
import com.thgiang.image.core.util.processors.ProcessorUtils.compositeForegroundOverBackground
import com.thgiang.image.core.util.processors.ProcessorUtils.toArgbBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PortraitProcessor {
    private const val TAG = "PortraitProcessor"

    suspend fun applyPortrait(
        context: Context,
        bitmap: Bitmap,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val repo = backgroundRemoverRepository
            val foreground = repo.getForegroundBitmap(source).getOrNull()
                ?: run {
                    if (!source.isRecycled) source.recycle()
                    return@runCatching null
                }

            val blurred = blurBitmapForPortraitExport(source, blurRadius.coerceIn(0f, 25f))
            if (blurred !== source && !source.isRecycled) source.recycle()

            val layered = DarkenProcessor.applyDarkenVignetteToBitmap(
                base = blurred,
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            if (layered !== blurred && !blurred.isRecycled) blurred.recycle()

            val output = compositeForegroundOverBackground(layered, foreground)
            if (!layered.isRecycled) layered.recycle()
            if (!foreground.isRecycled) foreground.recycle()
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
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val blurred = blurBitmapForPortraitExport(source, blurRadius.coerceIn(0f, 25f))
            if (blurred !== source && !source.isRecycled) source.recycle()

            val layered = DarkenProcessor.applyDarkenVignetteToBitmap(
                base = blurred,
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            if (layered !== blurred && !blurred.isRecycled) blurred.recycle()

            val output = compositeForegroundOverBackground(layered, foreground)
            if (!layered.isRecycled) layered.recycle()
            output
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }
}
