package com.thgiang.image.studio.ui.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.graphics.withSave
import com.thgiang.image.core.util.processors.PortraitProcessor
import com.thgiang.image.core.util.processors.ProcessorUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Render Engine v2 - Reusable shadow cache, downsampled template, inBitmap reuse
 */
class EditorRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapPool: EditorBitmapPool
) {
    
    data class RenderRequest(
        val templateAssetPath: String,
        val foregroundUri: Uri,
        val templateSize: androidx.compose.ui.unit.IntSize,
        val viewport: EditorViewport,
        val appearance: EditorAppearance,
        val baseSize: androidx.compose.ui.unit.IntSize,
        val cropRatio: CropRatio
    )
    
    // Cache shadow bitmap để tránh re-blur mỗi frame
    private data class ShadowCache(
        val sourceUri: Uri,
        val intensity: Float,
        val shadowBitmap: Bitmap,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    @Volatile
    private var shadowCache: ShadowCache? = null
    private val shadowCacheLock = Any()
    private val maxShadowCacheAgeMs = 5000L

    suspend fun render(request: RenderRequest): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            // 1. Load template with downsampling if needed
            val template = loadTemplateBitmap(
                request.templateAssetPath,
                request.templateSize.width,
                request.templateSize.height
            ) ?: throw IllegalStateException("Cannot load template: ${request.templateAssetPath}")
            
            // 2. Decode foreground
            val foreground = ProcessorUtils.decodeBitmapFromUri(context, request.foregroundUri)
                ?: throw IllegalStateException("Cannot decode foreground: ${request.foregroundUri}")
            
            // 3. Obtain result bitmap from pool
            val result = bitmapPool.obtain(
                request.templateSize.width, 
                request.templateSize.height
            )
            
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            
            // 4. Draw template background
            canvas.drawBitmap(template, 0f, 0f, paint)
            
            // 5. Calculate transform
            val state = request.viewport
            val baseW = request.baseSize.width.toFloat()
            val baseH = request.baseSize.height.toFloat()
            val drawW = baseW * state.scale
            val drawH = baseH * state.scale
            val centerX = (request.templateSize.width - drawW) / 2f
            val centerY = (request.templateSize.height - drawH) / 2f
            val drawX = centerX + state.offset.x
            val drawY = centerY + state.offset.y
            
            // 6. Shadow with caching
            if (request.appearance.shadowIntensity > 0.05f) {
                renderShadowCached(
                    canvas = canvas,
                    foreground = foreground,
                    state = state,
                    cropRatio = request.cropRatio,
                    baseW = baseW,
                    baseH = baseH,
                    drawX = drawX,
                    drawY = drawY,
                    intensity = request.appearance.shadowIntensity,
                    shadowAngle = request.appearance.shadowAngle,
                    shadowDistance = request.appearance.shadowDistance,
                    shadowColorArgb = request.appearance.shadowColorArgb,
                    sourceUri = request.foregroundUri
                )
            }
            
            // 7. Foreground with transform
            paint.alpha = (request.appearance.alpha * 255).toInt().coerceIn(0, 255)
            
            val scaleX = (baseW / foreground.width) * state.scale * (if (state.flippedH) -1f else 1f)
            val scaleY = (baseH / foreground.height) * state.scale * (if (state.flippedV) -1f else 1f)
            
            canvas.withSave {
                translate(drawX, drawY)
                scale(scaleX, scaleY)
                rotate(state.rotation, foreground.width / 2f, foreground.height / 2f)
                
                // Crop clipping (local coordinates)
                val croppedSize = request.cropRatio.calculateSize(foreground.width.toFloat(), foreground.height.toFloat())
                val left = (foreground.width - croppedSize.width) / 2f
                val top = (foreground.height - croppedSize.height) / 2f
                clipRect(left, top, left + croppedSize.width, top + croppedSize.height)
                
                val fgX = if (state.flippedH) -foreground.width.toFloat() else 0f
                val fgY = if (state.flippedV) -foreground.height.toFloat() else 0f
                drawBitmap(foreground, fgX, fgY, paint)
            }
            
            // Cleanup: recycle foreground but NOT template (may be reused)
            foreground.recycle()
            
            // Return template to pool if possible
            bitmapPool.recycle(template)
            
            result
        }
    }
    
    private suspend fun renderShadowCached(
        canvas: Canvas,
        foreground: Bitmap,
        state: EditorViewport,
        cropRatio: CropRatio,
        baseW: Float,
        baseH: Float,
        drawX: Float,
        drawY: Float,
        intensity: Float,
        shadowAngle: Float,
        shadowDistance: Float,
        shadowColorArgb: Int,
        sourceUri: Uri
    ) {
        // Check cache validity
        val cached = synchronized(shadowCacheLock) {
            val current = shadowCache
            if (current != null && 
                current.sourceUri == sourceUri && 
                current.intensity == intensity &&
                System.currentTimeMillis() - current.timestamp < maxShadowCacheAgeMs) {
                current.shadowBitmap
            } else {
                null
            }
        }
        
        val shadow = cached ?: run {
            // Generate new shadow
            val newShadow = PortraitProcessor.applyBlurOnly(
                foreground,
                shadowBlurRadiusFromIntensity(intensity)
            )
                ?: return
            
            synchronized(shadowCacheLock) {
                // Evict old cache
                shadowCache?.shadowBitmap?.let { 
                    if (!it.isRecycled) bitmapPool.recycle(it) 
                }
                shadowCache = ShadowCache(sourceUri, intensity, newShadow)
            }
            newShadow
        }
        
        if (shadow.isRecycled) return
        
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (shadowOpacityFromIntensity(intensity) * 255f).toInt().coerceIn(0, 255)
            colorFilter = PorterDuffColorFilter(shadowColorArgb, PorterDuff.Mode.SRC_ATOP)
        }
        
        val scaleX = (baseW / foreground.width) * state.scale * (if (state.flippedH) -1f else 1f)
        val scaleY = (baseH / foreground.height) * state.scale * (if (state.flippedV) -1f else 1f)
        
        // Calculate dynamic shadow offsets using trigonometry
        val angleRad = Math.toRadians(shadowAngle.toDouble())
        val dx = (shadowDistance * cos(angleRad)).toFloat()
        val dy = (shadowDistance * sin(angleRad)).toFloat()
        
        canvas.withSave {
            translate(drawX + dx, drawY + dy)
            scale(scaleX, scaleY)
            rotate(state.rotation, foreground.width / 2f, foreground.height / 2f)
            
            // Crop clipping (local coordinates)
            val croppedSize = cropRatio.calculateSize(foreground.width.toFloat(), foreground.height.toFloat())
            val left = (foreground.width - croppedSize.width) / 2f
            val top = (foreground.height - croppedSize.height) / 2f
            clipRect(left, top, left + croppedSize.width, top + croppedSize.height)
            
            val sdX = if (state.flippedH) -foreground.width.toFloat() else 0f
            val sdY = if (state.flippedV) -foreground.height.toFloat() else 0f
            drawBitmap(shadow, sdX, sdY, shadowPaint)
        }
    }
    
    private fun loadTemplateBitmap(assetPath: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            context.assets.open(assetPath).use { stream ->
                // Check if downsampling needed
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                
                val sampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, targetWidth, targetHeight)
                val decodeWidth = opts.outWidth / sampleSize
                val decodeHeight = opts.outHeight / sampleSize
                
                context.assets.open(assetPath).use { stream2 ->
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = true
                        
                        // Try to obtain an exact matching size bitmap from pool to prevent gc/allocations
                        val reuseBitmap = if (decodeWidth > 0 && decodeHeight > 0) {
                            bitmapPool.obtain(decodeWidth, decodeHeight)
                        } else {
                            null
                        }
                        inBitmap = reuseBitmap
                    }
                    try {
                        BitmapFactory.decodeStream(stream2, null, decodeOpts)
                    } catch (e: IllegalArgumentException) {
                        // Catch inBitmap dimension/format mismatches safely and fall back to fresh decode
                        val fallbackOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        context.assets.open(assetPath).use { stream3 ->
                            BitmapFactory.decodeStream(stream3, null, fallbackOpts)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    fun clearShadowCache() {
        synchronized(shadowCacheLock) {
            shadowCache?.shadowBitmap?.let { 
                if (!it.isRecycled) bitmapPool.recycle(it) 
            }
            shadowCache = null
        }
    }
}
