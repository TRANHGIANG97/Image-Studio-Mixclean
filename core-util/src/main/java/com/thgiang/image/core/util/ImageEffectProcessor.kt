package com.thgiang.image.core.util
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.model.PresetStyle
import com.thgiang.image.core.util.processors.*
import com.thgiang.image.core.util.processors.ProcessorUtils.toArgbBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cổng vào chính (Facade) cho các hiệu ứng hình ảnh.
 * Logic chi tiết được tách ra các Processor riêng biệt trong package processors để dễ bảo trì.
 */
object ImageEffectProcessor {

    private const val TAG = "ImageEffectProcessor"

    suspend fun applyBlur(
        context: Context,
        uri: Uri,
        radius: Float
    ): Bitmap? = runCatching {
        ProcessorUtils.decodeBitmapFromUri(context, uri)?.let { source ->
            applyBlur(source, radius).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()

    suspend fun applyBlur(
        bitmap: Bitmap,
        radius: Float
    ): Bitmap? = PortraitProcessor.applyBlurOnly(bitmap, radius)

    // --- Portrait ---
    suspend fun applyPortrait(
        context: Context,
        uri: Uri,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        ProcessorUtils.decodeBitmapFromUri(context, uri)?.let { source ->
            PortraitProcessor.applyPortrait(context, source, blurRadius, darkenAlpha, vignette, backgroundRemoverRepository).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()

    suspend fun applyPortrait(
        context: Context,
        bitmap: Bitmap,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = PortraitProcessor.applyPortrait(context, bitmap, blurRadius, darkenAlpha, vignette, backgroundRemoverRepository)

    suspend fun applyPortraitCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean
    ): Bitmap? = PortraitProcessor.applyPortraitCached(bitmap, foreground, blurRadius, darkenAlpha, vignette)

    // --- Clean ---
    suspend fun applyClean(
        context: Context,
        uri: Uri,
        intensity: Float,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        ProcessorUtils.decodeBitmapFromUri(context, uri)?.let { source ->
            CleanProcessor.applyClean(context, source, intensity, backgroundRemoverRepository).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()

    suspend fun applyClean(
        context: Context,
        bitmap: Bitmap,
        intensity: Float,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = CleanProcessor.applyClean(context, bitmap, intensity, backgroundRemoverRepository)

    suspend fun applyCleanCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        intensity: Float
    ): Bitmap? = CleanProcessor.applyCleanCached(bitmap, foreground, intensity)

    // --- Darken ---
    suspend fun applyDarken(
        context: Context,
        uri: Uri,
        intensity: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        ProcessorUtils.decodeBitmapFromUri(context, uri)?.let { source ->
            DarkenProcessor.applyDarken(context, source, intensity, vignette, backgroundRemoverRepository).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()

    suspend fun applyDarken(
        context: Context,
        bitmap: Bitmap,
        intensity: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = DarkenProcessor.applyDarken(context, bitmap, intensity, vignette, backgroundRemoverRepository)

    suspend fun applyDarkenCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        intensity: Float,
        vignette: Boolean
    ): Bitmap? = DarkenProcessor.applyDarkenCached(bitmap, foreground, intensity, vignette)

    // --- Utilities ---
    suspend fun extractForeground(
        context: Context,
        bitmap: Bitmap,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val repo = backgroundRemoverRepository
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val fg = repo.getForegroundBitmap(source).getOrNull()
            if (!source.isRecycled) source.recycle()
            fg
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    suspend fun applyBackground(
        foreground: Bitmap,
        backgroundType: BackgroundType,
        backgroundColor: Int? = null,
        backgroundGradient: IntArray? = null,
        backgroundImage: Bitmap? = null,
        backgroundPreset: PresetStyle? = null
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val width = foreground.width
            val height = foreground.height
            
            val bgBitmap = when (backgroundType) {
                BackgroundType.COLOR -> ProcessorUtils.createColorBitmap(width, height, backgroundColor ?: android.graphics.Color.WHITE)
                BackgroundType.GRADIENT -> ProcessorUtils.createGradientBitmap(width, height, backgroundGradient ?: intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK))
                BackgroundType.IMAGE -> {
                    if (backgroundImage == null) return@runCatching null
                    val scaledBg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(scaledBg)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                    
                    val scaleX = width.toFloat() / backgroundImage.width
                    val scaleY = height.toFloat() / backgroundImage.height
                    val scale = maxOf(scaleX, scaleY)
                    val dx = (width - backgroundImage.width * scale) / 2
                    val dy = (height - backgroundImage.height * scale) / 2
                    
                    canvas.save()
                    canvas.translate(dx, dy)
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(backgroundImage, 0f, 0f, paint)
                    canvas.restore()
                    scaledBg
                }
                BackgroundType.PRESET -> {
                    if (backgroundPreset == null) return@runCatching null
                    PresetRenderer.createPresetBitmap(width, height, backgroundPreset)
                }
                else -> return@runCatching null
            }
            
            val result = ProcessorUtils.compositeForegroundOverBackground(bgBitmap, foreground)
            if (!bgBitmap.isRecycled) bgBitmap.recycle()
            result
        }.onFailure { e -> ProcessorUtils.logOom(TAG, e) }.getOrNull()
    }

    enum class BackgroundType {
        COLOR, GRADIENT, IMAGE, PRESET
    }
}
