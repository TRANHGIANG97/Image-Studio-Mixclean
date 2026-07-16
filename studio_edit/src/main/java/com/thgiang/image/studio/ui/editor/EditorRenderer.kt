package com.thgiang.image.studio.ui.editor
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*
import com.thgiang.image.studio.ui.editor.mapper.EditorElevationMapper.drawShapeElevation
import com.thgiang.image.studio.ui.editor.mapper.hasShape3DDepth
import com.thgiang.image.studio.ui.editor.mapper.supportsTextElevation
import com.thgiang.image.studio.ui.editor.mapper.EditorShadowMapper.drawShapeDropShadow

import com.thgiang.image.studio.ui.editor.model.*

import android.content.Context
import android.graphics.*
import android.graphics.Paint.Align
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withSave
import com.thgiang.image.core.util.processors.ProcessorUtils
import com.thgiang.image.studio.util.openAssetSourceInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Render Engine v2 - Reusable shadow cache, downsampled template, inBitmap reuse
 */
class EditorRenderer(
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
        val blurRadius: Float,
        val colorArgb: Int,
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
                if (!layer.isVisible) continue
                when (layer.type) {
                    LayerType.SHAPE, LayerType.TEXT, LayerType.SHAPE_TEXT -> {
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
                        val baseW = layer.shapeWidthPx
                        val baseH = layer.shapeHeightPx
                        val drawW = baseW * state.scale
                        val drawH = baseH * state.scale
                        val centerX = (request.templateSize.width - drawW) / 2f
                        val centerY = (request.templateSize.height - drawH) / 2f
                        val drawX = centerX + state.offset.x
                        val drawY = centerY + state.offset.y

                        // Drop shadow must use normal compositing (not the layer blend mode),
                        // otherwise fringe colors / lighten-screen modes turn it neon.
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
                                appearance = layer.appearance,
                                sourceUri = fgUri,
                            )
                        }

                        canvas.withBlendLayer(layer.blendMode, layer.appearance.alpha) {
                            paint.alpha = 255

                            val scaleX = (baseW / foreground.width) * state.scale * (if (state.flippedH) -1f else 1f)
                            val scaleY = (baseH / foreground.height) * state.scale * (if (state.flippedV) -1f else 1f)

                            withSave {
                                translate(drawX, drawY)
                                scale(scaleX, scaleY)
                                rotate(state.rotation, foreground.width / 2f, foreground.height / 2f)

                                val croppedSize = layer.cropRatio.calculateSize(foreground.width.toFloat(), foreground.height.toFloat())
                                val left = (foreground.width - croppedSize.width) / 2f
                                val top = (foreground.height - croppedSize.height) / 2f
                                clipRect(left, top, left + croppedSize.width, top + croppedSize.height)

                                val ox = layer.cropOffsetX * (foreground.width / layer.shapeWidthPx.coerceAtLeast(1f))
                                val oy = layer.cropOffsetY * (foreground.height / layer.shapeHeightPx.coerceAtLeast(1f))
                                translate(ox, oy)

                                val fgX = if (state.flippedH) -foreground.width.toFloat() else 0f
                                val fgY = if (state.flippedV) -foreground.height.toFloat() else 0f
                                drawBitmap(foreground, fgX, fgY, paint)
                            }
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
        appearance: EditorAppearance,
        sourceUri: Uri
    ) {
        val blurRadius = appearance.resolvedShadowBlurRadius()
        val opaqueShadowColor = appearance.opaqueShadowColorArgb()
        // Check cache validity
        val cached = synchronized(shadowCacheLock) {
            val current = shadowCache
            if (current != null &&
                current.sourceUri == sourceUri &&
                current.intensity == appearance.shadowIntensity &&
                current.blurRadius == blurRadius &&
                current.colorArgb == opaqueShadowColor &&
                System.currentTimeMillis() - current.timestamp < maxShadowCacheAgeMs) {
                current.shadowBitmap
            } else {
                null
            }
        }

        val shadow = cached ?: run {
            // Alpha-mask silhouette + alpha-aware blur (never tint a blurred full-color cutout).
            val newShadow = EditorShadowMapper.buildProductDropShadowBitmap(
                foreground = foreground,
                blurRadius = blurRadius,
                shadowColorArgb = opaqueShadowColor,
            ) ?: return

            synchronized(shadowCacheLock) {
                // Evict old cache
                shadowCache?.shadowBitmap?.let {
                    if (!it.isRecycled) bitmapPool.recycle(it)
                }
                shadowCache = ShadowCache(
                    sourceUri = sourceUri,
                    intensity = appearance.shadowIntensity,
                    blurRadius = blurRadius,
                    colorArgb = opaqueShadowColor,
                    shadowBitmap = newShadow,
                )
            }
            newShadow
        }

        if (shadow.isRecycled) return

        // Bitmap is already colored; only modulate opacity here.
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (shadowOpacityFromIntensity(appearance.shadowIntensity) * appearance.alpha * 255f)
                .toInt().coerceIn(0, 255)
        }

        val scaleX = (baseW / foreground.width) * state.scale * (if (state.flippedH) -1f else 1f)
        val scaleY = (baseH / foreground.height) * state.scale * (if (state.flippedV) -1f else 1f)

        // Calculate dynamic shadow offsets using trigonometry
        val (dx, dy) = shadowOffset(appearance.shadowAngle, appearance.shadowDistance)

        // Expand clip slightly so soft blur is not hard-cropped into a neon ring.
        val blurPad = (blurRadius * 3f).coerceAtLeast(0f)

        canvas.withSave {
            translate(drawX + dx, drawY + dy)
            scale(scaleX, scaleY)
            rotate(state.rotation, foreground.width / 2f, foreground.height / 2f)

            // Crop clipping (local coordinates)
            val croppedSize = cropRatio.calculateSize(foreground.width.toFloat(), foreground.height.toFloat())
            val left = (foreground.width - croppedSize.width) / 2f - blurPad
            val top = (foreground.height - croppedSize.height) / 2f - blurPad
            clipRect(
                left,
                top,
                left + croppedSize.width + blurPad * 2f,
                top + croppedSize.height + blurPad * 2f,
            )

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
        val shadowW = layer.shapeWidthPx * state.scale
        val shadowH = layer.shapeHeightPx * state.scale
        val left = (templateWidth - shadowW) / 2f + state.offset.x
        val top = (templateHeight - shadowH) / 2f + state.offset.y
        val rawIntensity = layer.appearance.shadowIntensity
        val intensity = if (rawIntensity > 0f) rawIntensity else 0.70f
        val color = layer.appearance.shadowColorArgb
        val colorAlpha = (color ushr 24) and 0xFF
        val alpha = ((colorAlpha / 255f) * layer.appearance.alpha * intensity * 255f).toInt().coerceIn(0, 255)

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
        templateHeight: Int,
    ) {
        val state = layer.viewport
        val shapeW = layer.shapeWidthPx * state.scale
        val shapeH = layer.shapeHeightPx * state.scale
        val left = (templateWidth - shapeW) / 2f + state.offset.x
        val top = (templateHeight - shapeH) / 2f + state.offset.y

        val shapeAlpha = (layer.shapeColorArgb ushr 24) and 0xFF
        val shouldRenderShape = layer.shouldRenderFrameContent
        val shouldRenderTextOnlyDecor = layer.hasTextOnlyBackgroundDecor
        val drawShapeType = layer.backgroundDecorShapeType
        val hasShapeFill = EditorShapeGeometry.isFilledShape(
            layer.shapeType,
            shapeAlpha,
            layer.fillGradient != null,
        )
        val canDrawShapeShadow = (shouldRenderShape || shouldRenderTextOnlyDecor) &&
            if (EditorShapeGeometry.isTextOnlyShape(layer.shapeType)) {
                hasShapeFill || layer.hasShapeBorder
            } else {
                hasShapeFill || layer.hasShapeBorder || layer.appearance.shadowIntensity > 0.05f
            }

        canvas.withBlendLayer(layer.blendMode, layer.appearance.alpha) {
            withSave {
                rotate(state.rotation, left + shapeW / 2f, top + shapeH / 2f)
                val scaleX = if (state.flippedH) -1f else 1f
                val scaleY = if (state.flippedV) -1f else 1f
                if (scaleX != 1f || scaleY != 1f) {
                    scale(scaleX, scaleY, left + shapeW / 2f, top + shapeH / 2f)
                }

                if (
                    shouldRenderShape &&
                    layer.isFrameLayer &&
                    layer.supportsShapeElevation &&
                    layer.appearance.hasShape3DDepth(state.scale) &&
                    layer.appearance.appliesShapeElevation()
                ) {
                    drawShapeElevation(
                        shapeType = layer.shapeType,
                        appearance = layer.appearance,
                        fillColorArgb = layer.shapeColorArgb,
                        left = left,
                        top = top,
                        shapeW = shapeW,
                        shapeH = shapeH,
                        cornerRadiusX = layer.cornerRadiusX,
                        cornerRadiusY = layer.cornerRadiusY,
                        scale = state.scale,
                        pathData = layer.pathData,
                        polygonPoints = layer.polygonPoints,
                    )
                }

                if (canDrawShapeShadow && layer.appearance.shadowIntensity > 0.05f) {
                    val (shadowDx, shadowDy) = EditorShadowMapper.shadowOffsetLocalPx(
                        layer.appearance,
                        state.scale,
                        state.rotation,
                    )
                    val shadowPaint = EditorShadowMapper.configureDropShadowPaint(
                        layer.appearance,
                        renderScale = state.scale,
                    )
                    drawShapeGeometry(
                        shapeType = drawShapeType,
                        left = left + shadowDx,
                        top = top + shadowDy,
                        shapeW = shapeW,
                        shapeH = shapeH,
                        cornerRadiusX = layer.cornerRadiusX,
                        cornerRadiusY = layer.cornerRadiusY,
                        paint = shadowPaint,
                        pathData = layer.pathData,
                        polygonPoints = layer.polygonPoints,
                    )
                }

                val alpha = (layer.appearance.alpha * 255).toInt().coerceIn(0, 255)
                if ((shouldRenderShape || shouldRenderTextOnlyDecor) && hasShapeFill) {
                    val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = layer.shapeColorArgb
                        this.alpha = if (shapeAlpha > 0) {
                            ((shapeAlpha / 255f) * alpha).toInt().coerceIn(0, 255)
                        } else {
                            alpha
                        }
                        EditorGradientMapper.toAndroidShader(
                            layer.fillGradient,
                            left,
                            top,
                            shapeW,
                            shapeH,
                            layer.shapeColorArgb,
                        )?.let { shader = it }
                    }
                    drawShapeGeometry(
                        shapeType = drawShapeType,
                        left = left,
                        top = top,
                        shapeW = shapeW,
                        shapeH = shapeH,
                        cornerRadiusX = layer.cornerRadiusX,
                        cornerRadiusY = layer.cornerRadiusY,
                        paint = shapePaint,
                        pathData = layer.pathData,
                        polygonPoints = layer.polygonPoints,
                    )
                }

                if ((shouldRenderShape || shouldRenderTextOnlyDecor) && layer.hasShapeBorder) {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        EditorStrokeMapper.configureStrokePaint(
                            paint = this,
                            strokeColorArgb = layer.resolveShapeBorderColorArgb()!!,
                            strokeWidthPx = layer.resolveStrokeWidthPx() * state.scale,
                            strokeDashArray = layer.strokeDashArray,
                            strokeDashGapPx = layer.strokeDashGapPx,
                            alpha = alpha,
                            renderScale = state.scale,
                        )
                    }
                    drawShapeGeometry(
                        shapeType = drawShapeType,
                        left = left,
                        top = top,
                        shapeW = shapeW,
                        shapeH = shapeH,
                        cornerRadiusX = layer.cornerRadiusX,
                        cornerRadiusY = layer.cornerRadiusY,
                        paint = strokePaint,
                        pathData = layer.pathData,
                        polygonPoints = layer.polygonPoints,
                    )
                }

                if (layer.text.isNotBlank() && layer.shouldRenderLabelContent) {
                    if (layer.textForm.isActive) {
                        com.thgiang.image.studio.ui.editor.document.render.DocumentRenderPipeline.drawTextLayer(
                            canvas = canvas,
                            context = context,
                            layer = layer,
                            left = left,
                            top = top,
                            width = shapeW,
                            height = shapeH,
                            renderScale = state.scale,
                        )
                    } else {
                        drawShapeTextContent(canvas, layer, state, left, top, shapeW, shapeH, alpha)
                    }
                }
            }
        }
    }

    private fun drawShapeTextContent(
        canvas: Canvas,
        layer: EditorLayer,
        state: EditorViewport,
        left: Float,
        top: Float,
        shapeW: Float,
        shapeH: Float,
        alpha: Int,
    ) {
        val shouldRenderShape = layer.shouldRenderFrameContent
        val paddingX = if (shouldRenderShape) {
            val calcX = 12f * layer.viewport.scale * state.scale
            if (calcX > shapeW / 4f) shapeW / 4f else calcX
        } else {
            0f
        }
        val paddingY = if (shouldRenderShape) {
            val calcY = 6f * layer.viewport.scale * state.scale
            if (calcY > shapeH / 4f) shapeH / 4f else calcY
        } else {
            0f
        }

        EditorTextRenderMapper.drawFlatTextOnCanvas(
            canvas = canvas,
            layer = layer,
            left = left + paddingX,
            top = top + paddingY,
            width = shapeW - paddingX * 2f,
            height = shapeH - paddingY * 2f,
            renderScale = state.scale,
            context = context,
            alpha = alpha,
            rotationDeg = state.rotation,
        )
    }
}
