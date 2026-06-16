package com.thgiang.image.studio.ui.editor

import android.content.Context
import android.graphics.*
import android.graphics.Paint.Align
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withSave
import com.thgiang.image.core.util.processors.PortraitProcessor
import com.thgiang.image.core.util.processors.ProcessorUtils
import com.thgiang.image.studio.util.openAssetSourceInputStream
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
    
    data class MultiLayerRenderRequest(
        val templateAssetPath: String,
        val templateSize: androidx.compose.ui.unit.IntSize,
        val layers: List<EditorLayer>,
        val backgroundColorArgb: Int = android.graphics.Color.WHITE
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

    suspend fun renderLayers(request: MultiLayerRenderRequest): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            val template = loadTemplateBitmap(
                request.templateAssetPath,
                request.templateSize.width,
                request.templateSize.height,
                request.backgroundColorArgb
            )
            
            val result = bitmapPool.obtain(
                request.templateSize.width, 
                request.templateSize.height
            )
            
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            
            // Fill white để vùng transparent của template PNG không ra đen
            canvas.drawColor(request.backgroundColorArgb)
            canvas.drawBitmap(template, 0f, 0f, paint)
            
            for (layer in request.layers) {
                when (layer.type) {
                    LayerType.SHAPE_TEXT -> {
                        renderShapeTextLayer(canvas, layer, request.templateSize.width, request.templateSize.height)
                    }
                    LayerType.SHADOW_REGION -> {
                        renderShadowRegionLayer(canvas, layer, request.templateSize.width, request.templateSize.height)
                    }
                    LayerType.IMAGE -> {
                        val fgUriString = layer.product.foregroundUriString ?: continue
                        val fgUri = Uri.parse(fgUriString)
                        val foreground = ProcessorUtils.decodeBitmapFromUri(context, fgUri)
                            ?: continue

                        val state = layer.viewport
                        val baseW = layer.product.baseSize.width.toFloat()
                        val baseH = layer.product.baseSize.height.toFloat()
                        val drawW = baseW * state.scale
                        val drawH = baseH * state.scale
                        val centerX = (request.templateSize.width - drawW) / 2f
                        val centerY = (request.templateSize.height - drawH) / 2f
                        val drawX = centerX + state.offset.x
                        val drawY = centerY + state.offset.y

                        if (layer.appearance.shadowIntensity > 0.05f) {
                            renderShadowCached(
                                canvas = canvas,
                                foreground = foreground,
                                state = state,
                                cropRatio = layer.cropRatio,
                                baseW = baseW,
                                baseH = baseH,
                                drawX = drawX,
                                drawY = drawY,
                                intensity = layer.appearance.shadowIntensity,
                                shadowAngle = layer.appearance.shadowAngle,
                                shadowDistance = layer.appearance.shadowDistance,
                                shadowColorArgb = layer.appearance.shadowColorArgb,
                                sourceUri = fgUri
                            )
                        }

                        paint.alpha = (layer.appearance.alpha * 255).toInt().coerceIn(0, 255)

                        val scaleX = (baseW / foreground.width) * state.scale * (if (state.flippedH) -1f else 1f)
                        val scaleY = (baseH / foreground.height) * state.scale * (if (state.flippedV) -1f else 1f)

                        canvas.withSave {
                            translate(drawX, drawY)
                            scale(scaleX, scaleY)
                            rotate(state.rotation, foreground.width / 2f, foreground.height / 2f)

                            val croppedSize = layer.cropRatio.calculateSize(foreground.width.toFloat(), foreground.height.toFloat())
                            val left = (foreground.width - croppedSize.width) / 2f
                            val top = (foreground.height - croppedSize.height) / 2f
                            clipRect(left, top, left + croppedSize.width, top + croppedSize.height)

                            val fgX = if (state.flippedH) -foreground.width.toFloat() else 0f
                            val fgY = if (state.flippedV) -foreground.height.toFloat() else 0f
                            drawBitmap(foreground, fgX, fgY, paint)
                        }

                        foreground.recycle()
                    }
                }
            }
            
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

    private fun renderShadowRegionLayer(
        canvas: Canvas,
        layer: EditorLayer,
        templateWidth: Int,
        templateHeight: Int
    ) {
        val state = layer.viewport
        val shadowW = layer.product.baseSize.width.toFloat() * state.scale
        val shadowH = layer.product.baseSize.height.toFloat() * state.scale
        val left = (templateWidth - shadowW) / 2f + state.offset.x
        val top = (templateHeight - shadowH) / 2f + state.offset.y
        val intensity = layer.appearance.shadowIntensity.coerceIn(0f, 1f)
        val alpha = (layer.appearance.alpha * shadowOpacityFromIntensity(intensity) * 255f).toInt().coerceIn(0, 255)
        val color = layer.appearance.shadowColorArgb

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        canvas.withSave {
            rotate(state.rotation, left + shadowW / 2f, top + shadowH / 2f)
            val rect = RectF(left, top, left + shadowW, top + shadowH)
            paint.shader = RadialGradient(
                rect.centerX(),
                rect.centerY() + shadowH * 0.08f,
                maxOf(shadowW, shadowH) * 0.62f,
                intArrayOf(
                    (alpha shl 24) or (color and 0x00FFFFFF),
                    (((alpha * 0.55f).toInt().coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF),
                    0x00000000
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
            drawOval(rect, paint)
        }
    }
    
    private fun getInputStreamForPath(path: String): java.io.InputStream? {
        return context.openAssetSourceInputStream(path)
    }

    private fun loadTemplateBitmap(
        assetPath: String,
        targetWidth: Int,
        targetHeight: Int,
        fallbackColorArgb: Int
    ): Bitmap {
        if (assetPath.isBlank() || assetPath == "null") {
            return bitmapPool.obtain(targetWidth, targetHeight).apply {
                eraseColor(fallbackColorArgb)
            }
        }
        return try {
            var decoded: Bitmap? = null
            getInputStreamForPath(assetPath)?.use { stream ->
                // Check if downsampling needed
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                
                val sampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, targetWidth, targetHeight)
                val decodeWidth = opts.outWidth / sampleSize
                val decodeHeight = opts.outHeight / sampleSize
                
                getInputStreamForPath(assetPath)?.use { stream2 ->
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
                        decoded = BitmapFactory.decodeStream(stream2, null, decodeOpts)
                    } catch (e: IllegalArgumentException) {
                        // Catch inBitmap dimension/format mismatches safely and fall back to fresh decode
                        val fallbackOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        getInputStreamForPath(assetPath)?.use { stream3 ->
                            decoded = BitmapFactory.decodeStream(stream3, null, fallbackOpts)
                        }
                    }
                }
            }
            decoded ?: bitmapPool.obtain(targetWidth, targetHeight).apply {
                eraseColor(fallbackColorArgb)
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorRenderer", "Failed to load template bitmap for $assetPath, using fallback white bitmap", e)
            bitmapPool.obtain(targetWidth, targetHeight).apply {
                eraseColor(fallbackColorArgb)
            }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Shape & Text Layer Renderer (High-Res Export)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Render a SHAPE_TEXT layer directly onto the export canvas using
     * Android's native Paint / Path / StaticLayout for crisp, resolution-independent output.
     */
    private fun renderShapeTextLayer(
        canvas: Canvas,
        layer: EditorLayer,
        templateWidth: Int,
        templateHeight: Int
    ) {
        val state  = layer.viewport
        val shapeW = layer.shapeWidthPx  * state.scale
        val shapeH = layer.shapeHeightPx * state.scale

        // Position the shape centered at the canvas center + layer offset
        val left = (templateWidth  - shapeW) / 2f + state.offset.x
        val top  = (templateHeight - shapeH) / 2f + state.offset.y

        canvas.withSave {
            // Apply rotation around shape centre
            rotate(state.rotation, left + shapeW / 2f, top + shapeH / 2f)

            // Global opacity
            val alpha = (layer.appearance.alpha * 255).toInt().coerceIn(0, 255)

            // ── Shape fill ───────────────────────────────────────────────
            val shapeAlpha = (layer.shapeColorArgb ushr 24) and 0xFF
            if (shapeAlpha > 0) {
                val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style      = Paint.Style.FILL
                    color      = layer.shapeColorArgb
                    this.alpha = ((shapeAlpha / 255f) * alpha).toInt().coerceIn(0, 255)
                }

                when (layer.shapeType) {
                    ShapeType.PILL -> {
                        val rx = shapeH / 2f
                        canvas.drawRoundRect(left, top, left + shapeW, top + shapeH, rx, rx, shapePaint)
                    }
                    ShapeType.CARD -> {
                        val rx = shapeH * 0.16f
                        canvas.drawRoundRect(left, top, left + shapeW, top + shapeH, rx, rx, shapePaint)
                    }
                    ShapeType.TEARDROP -> {
                        canvas.drawPath(buildTeardropExportPath(left, top, shapeW, shapeH), shapePaint)
                    }
                    ShapeType.CIRCLE -> {
                        canvas.drawOval(left, top, left + shapeW, top + shapeH, shapePaint)
                    }
                    ShapeType.STAR -> {
                        canvas.drawPath(buildStarExportPath(left, top, shapeW, shapeH), shapePaint)
                    }
                    ShapeType.HEXAGON -> {
                        canvas.drawPath(buildHexagonExportPath(left, top, shapeW, shapeH), shapePaint)
                    }
                }
            }

            // ── Text ──────────────────────────────────────────────────────
            val textSizePx = layer.textSizeSp * context.resources.displayMetrics.scaledDensity * state.scale

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color      = layer.textColorArgb
                this.alpha = alpha
                textSize   = textSizePx
                textAlign  = Align.CENTER
                isFakeBoldText = true
                layer.fontFamily?.let { familyName ->
                    val customTf = kotlinx.coroutines.runBlocking {
                        com.thgiang.image.studio.util.FontDownloader.getTypeface(context, familyName)
                    }
                    if (customTf != null) {
                        typeface = customTf
                    }
                }
            }

            val paddingH  = shapeW * 0.08f
            val textWidth = (shapeW - paddingH * 2).toInt().coerceAtLeast(1)

            @Suppress("DEPRECATION")
            val staticLayout = StaticLayout(
                layer.text,
                textPaint,
                textWidth,
                android.text.Layout.Alignment.ALIGN_CENTER,
                1f, 0f, true
            )

            // Vertically centre the text block inside the shape
            val textBlockH = staticLayout.height.toFloat()
            val textTop    = top + (shapeH - textBlockH) / 2f

            canvas.withSave {
                translate(left + paddingH + textWidth / 2f, textTop)
                staticLayout.draw(this)
            }
        }
    }

    /** Build the teardrop android.graphics.Path for the export canvas */
    private fun buildTeardropExportPath(
        left: Float,
        top: Float,
        w: Float,
        h: Float
    ): android.graphics.Path {
        val r   = minOf(w, h) * 0.38f
        val ptr = h * 0.22f

        return android.graphics.Path().apply {
            moveTo(left + r, top)
            lineTo(left + w - r, top)
            cubicTo(left + w, top, left + w, top + r, left + w, top + r)
            lineTo(left + w, top + h - r - ptr)
            cubicTo(left + w, top + h - ptr, left + w - r, top + h - ptr, left + w - r, top + h - ptr)
            lineTo(left + r * 0.5f, top + h - ptr)
            cubicTo(left, top + h - ptr, left, top + h, left, top + h)
            lineTo(left, top + h - ptr - r)
            cubicTo(left, top + r, left + r, top, left + r, top)
            close()
        }
    }

    private fun buildStarExportPath(
        left: Float, top: Float, w: Float, h: Float
    ): android.graphics.Path {
        val centerX = left + w / 2f
        val centerY = top + h / 2f
        val outerRadius = minOf(w, h) / 2f
        val innerRadius = outerRadius * 0.4f
        
        return android.graphics.Path().apply {
            var angle = -Math.PI / 2.0
            val angleStep = Math.PI / 5.0
            
            moveTo(
                centerX + (outerRadius * Math.cos(angle)).toFloat(),
                centerY + (outerRadius * Math.sin(angle)).toFloat()
            )
            for (i in 1..10) {
                angle += angleStep
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                lineTo(
                    centerX + (radius * Math.cos(angle)).toFloat(),
                    centerY + (radius * Math.sin(angle)).toFloat()
                )
            }
            close()
        }
    }

    private fun buildHexagonExportPath(
        left: Float, top: Float, w: Float, h: Float
    ): android.graphics.Path {
        val r = minOf(w, h) / 2f
        val centerX = left + w / 2f
        val centerY = top + h / 2f
        
        return android.graphics.Path().apply {
            for (i in 0..5) {
                val angle = Math.PI / 3.0 * i
                val px = centerX + (r * Math.cos(angle)).toFloat()
                val py = centerY + (r * Math.sin(angle)).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
    }
}
