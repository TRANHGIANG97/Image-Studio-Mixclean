package com.thgiang.image.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.blurBitmapForPortraitExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

object ImageEffectProcessor {

    private const val TAG = "ImgEffectProcessor"

    suspend fun applyBlur(
        context: Context,
        uri: Uri,
        radius: Float
    ): Bitmap? = runCatching {
        decodeBitmapFromUri(context, uri)?.let { source ->
            applyBlur(source, radius).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> logOom(e) }.getOrNull()

    suspend fun applyPortrait(
        context: Context,
        uri: Uri,
        blurRadius: Float,
        darkenAlpha: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        decodeBitmapFromUri(context, uri)?.let { source ->
            applyPortrait(context, source, blurRadius, darkenAlpha, vignette, backgroundRemoverRepository).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> logOom(e) }.getOrNull()

    suspend fun applyClean(
        context: Context,
        uri: Uri,
        intensity: Float,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        decodeBitmapFromUri(context, uri)?.let { source ->
            applyClean(context, source, intensity, backgroundRemoverRepository).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> logOom(e) }.getOrNull()

    suspend fun applyDarken(
        context: Context,
        uri: Uri,
        intensity: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        decodeBitmapFromUri(context, uri)?.let { source ->
            applyDarken(context, source, intensity, vignette, backgroundRemoverRepository).also {
                if (!source.isRecycled) source.recycle()
            }
        }
    }.onFailure { e -> logOom(e) }.getOrNull()

    suspend fun applyBlur(
        bitmap: Bitmap,
        radius: Float
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val source = bitmap.toArgbBitmap() ?: return@runCatching null
            val result = blurBitmapForPortraitExport(source, radius.coerceIn(0f, 25f))
            if (result !== source && !source.isRecycled) source.recycle()
            result
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

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

            val layered = applyDarkenVignetteToBitmap(
                base = blurred,
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            if (layered !== blurred && !blurred.isRecycled) blurred.recycle()

            val output = compositeForegroundOverBackground(layered, foreground)
            if (!layered.isRecycled) layered.recycle()
            if (!foreground.isRecycled) foreground.recycle()
            output
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

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
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

    suspend fun applyDarken(
        context: Context,
        bitmap: Bitmap,
        intensity: Float,
        vignette: Boolean,
        backgroundRemoverRepository: BackgroundRemoverRepository
    ): Bitmap? = runCatching {
        val clamped = intensity.coerceIn(0f, 1f)
        val darkenAlpha = 0.15f + clamped * 0.35f
        applyPortrait(
            context = context,
            bitmap = bitmap,
            blurRadius = 0f,
            darkenAlpha = darkenAlpha,
            vignette = vignette,
            backgroundRemoverRepository = backgroundRemoverRepository
        )
    }.onFailure { e -> logOom(e) }.getOrNull()

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
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

    /** Portrait dùng foreground đã cache — bỏ qua ML Kit */
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

            val layered = applyDarkenVignetteToBitmap(
                base = blurred,
                darkenAlpha = darkenAlpha.coerceIn(0f, 1f),
                vignette = vignette
            )
            if (layered !== blurred && !blurred.isRecycled) blurred.recycle()

            val output = compositeForegroundOverBackground(layered, foreground)
            if (!layered.isRecycled) layered.recycle()
            output
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

    /** Clean dùng foreground đã cache — bỏ qua ML Kit */
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
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

    /** Darken dùng foreground đã cache — bỏ qua ML Kit */
    suspend fun applyDarkenCached(
        bitmap: Bitmap,
        foreground: Bitmap,
        intensity: Float,
        vignette: Boolean
    ): Bitmap? = runCatching {
        val clamped = intensity.coerceIn(0f, 1f)
        val darkenAlpha = 0.15f + clamped * 0.35f
        applyPortraitCached(
            bitmap = bitmap,
            foreground = foreground,
            blurRadius = 0f,
            darkenAlpha = darkenAlpha,
            vignette = vignette
        )
    }.onFailure { e -> logOom(e) }.getOrNull()

    fun downscaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val maxDim = maxOf(w, h)
        if (maxDim <= maxDimension) return source
        val scale = maxDimension / maxDim
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /** Decode URI với inSampleSize động dựa trên heap class. */
    private suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val maxSide = MemoryUtil.maxDecodeSide(context)
                val opts = BitmapFactory.Options().apply {
                    inMutable = false
                    if (maxSide > 0) {
                        inJustDecodeBounds = true
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, this)
                        }
                        inSampleSize = MemoryUtil.calculateInSampleSize(this, maxSide)
                        inJustDecodeBounds = false
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }?.toArgbBitmap()
            }.onFailure { e ->
                if (e is OutOfMemoryError) Log.e(TAG, "OOM decoding bitmap from URI", e)
            }.getOrNull()
        }

    private fun Bitmap.toArgbBitmap(): Bitmap? {
        return copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun compositeForegroundOverBackground(background: Bitmap, foreground: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        // Draw background
        canvas.drawBitmap(background, 0f, 0f, paint)
        
        // Scale foreground to fit if needed (center crop style or fit center)
        // Here we assume they should match size, but if not:
        if (foreground.width != background.width || foreground.height != background.height) {
            val scaleX = background.width.toFloat() / foreground.width
            val scaleY = background.height.toFloat() / foreground.height
            val scale = minOf(scaleX, scaleY)
            val dx = (background.width - foreground.width * scale) / 2
            val dy = (background.height - foreground.height * scale) / 2
            
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            canvas.drawBitmap(foreground, 0f, 0f, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(foreground, 0f, 0f, paint)
        }
        
        return result
    }

    fun createColorBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }

    fun createGradientBitmap(width: Int, height: Int, colors: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            colors, null, android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    suspend fun applyBackground(
        foreground: Bitmap,
        backgroundType: BackgroundType,
        backgroundColor: Int? = null,
        backgroundGradient: IntArray? = null,
        backgroundImage: Bitmap? = null
    ): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val width = foreground.width
            val height = foreground.height
            
            val bgBitmap = when (backgroundType) {
                BackgroundType.COLOR -> createColorBitmap(width, height, backgroundColor ?: Color.WHITE)
                BackgroundType.GRADIENT -> createGradientBitmap(width, height, backgroundGradient ?: intArrayOf(Color.WHITE, Color.BLACK))
                BackgroundType.IMAGE -> {
                    if (backgroundImage == null) return@runCatching null
                    // Scale background image to cover
                    val scaledBg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(scaledBg)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    
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
            }
            
            val result = compositeForegroundOverBackground(bgBitmap, foreground)
            if (!bgBitmap.isRecycled) bgBitmap.recycle()
            result
        }.onFailure { e -> logOom(e) }.getOrNull()
    }

    enum class BackgroundType {
        COLOR, GRADIENT, IMAGE
    }

    private fun applyDarkenVignetteToBitmap(
        base: Bitmap,
        darkenAlpha: Float,
        vignette: Boolean
    ): Bitmap {
        val a = darkenAlpha.coerceIn(0f, 1f)
        if (a <= 0f) return base

        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (!vignette) {
            paint.color = Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        } else {
            val w = out.width.toFloat()
            val h = out.height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = (min(w, h) * 0.72f).coerceAtLeast(1f)
            val shader = android.graphics.RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                    Color.argb(0, 0, 0, 0),
                    Color.argb((a * 0.65f * 255).toInt().coerceIn(0, 255), 0, 0, 0),
                    Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        return out
    }














    private fun logOom(e: Throwable) {
        if (e is OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in image processing", e)
        }
    }
}
