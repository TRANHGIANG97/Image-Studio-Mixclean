package com.thgiang.image.studio.ui.editor.mapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.appliesTextElevation
import com.thgiang.image.studio.ui.editor.model.depthShadowBlurPx
import com.thgiang.image.studio.util.FontDownloader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object TextElevationMapper {

    private const val TEXT_LAYOUT_INCLUDE_PAD = false

    /** Draw extrusion using the same StaticLayout metrics as [EditorTextRenderMapper.drawFlatTextOnCanvas]. */
    fun drawExtrusionBehindLayout(
        canvas: Canvas,
        displayText: String,
        translateX: Float,
        translateY: Float,
        textWidth: Int,
        alignment: Layout.Alignment,
        lineSpacing: Float,
        layer: EditorLayer,
        renderScale: Float,
        context: Context,
        textSizePx: Float,
        alpha: Int,
        referencePaint: TextPaint,
    ) {
        if (!layer.supportsTextElevation) return
        if (!layer.appearance.appliesTextElevation()) return
        val appearance = layer.appearance
        val depthPx = appearance.resolvedDepthSizePx(renderScale)
        if (depthPx <= 0.5f) return
        if (displayText.isBlank()) return

        val extrusionPaint = buildFillPaint(layer, textSizePx, alpha, context).apply {
            textSize = referencePaint.textSize
            letterSpacing = referencePaint.letterSpacing
            typeface = referencePaint.typeface
            textAlign = referencePaint.textAlign
        }
        val layout = createTextLayout(
            text = displayText,
            paint = extrusionPaint,
            maxWidth = textWidth,
            textAlign = layer.textAlign,
            lineHeight = layer.lineHeight,
        )

        val angleRad = Math.toRadians(appearance.resolvedExtrusionAngleDeg().toDouble())
        val dx = (cos(angleRad) * depthPx).toFloat()
        val dy = (sin(angleRad) * depthPx).toFloat()

        val faceColor = layer.resolveTextElevationColorArgb()
        val sideColor = resolveShapeDepthColor(faceColor, appearance.depthColorArgb)
        val backColor = resolveShapeDepthBackColor(sideColor)

        appearance.depthShadowBlurPx(renderScale)?.takeIf { it > 0.5f }?.let { blurPx ->
            drawSoftTextShadow(
                canvas = canvas,
                displayText = displayText,
                translateX = translateX,
                translateY = translateY,
                dx = dx * 0.35f,
                dy = dy * 0.35f,
                textWidth = textWidth,
                textAlign = layer.textAlign,
                lineHeight = layer.lineHeight,
                referencePaint = extrusionPaint,
                color = sideColor,
                alpha = (alpha * 0.45f).toInt().coerceIn(0, 255),
                blurPx = blurPx,
            )
        }

        val steps = max((depthPx / 1.5f).toInt(), 8).coerceAtMost(48)
        for (step in steps downTo 1) {
            val t = step.toFloat() / steps
            extrusionPaint.color = blendArgb(backColor, sideColor, t)
            canvas.save()
            canvas.translate(translateX + dx * t, translateY + dy * t)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    fun DrawScope.drawTextElevation(
        layer: EditorLayer,
        renderScale: Float,
        context: Context,
        textLayoutWidth: Float? = null,
        textLayoutHeight: Float? = null,
    ) {
        if (!layer.supportsTextElevation) return
        if (!layer.appearance.appliesTextElevation()) return
        if (layer.appearance.resolvedDepthSizePx(renderScale) <= 0.5f) return
        drawIntoCanvas { composeCanvas ->
            drawOnCanvas(
                canvas = composeCanvas.nativeCanvas,
                layer = layer,
                left = 0f,
                top = 0f,
                width = size.width,
                height = size.height,
                renderScale = renderScale,
                context = context,
                textLayoutWidth = textLayoutWidth,
                textLayoutHeight = textLayoutHeight,
            )
        }
    }

    fun drawOnCanvas(
        canvas: Canvas,
        layer: EditorLayer,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        renderScale: Float,
        context: Context,
        alpha: Int = (layer.appearance.alpha * 255f).toInt().coerceIn(0, 255),
        textLayoutWidth: Float? = null,
        textLayoutHeight: Float? = null,
    ) {
        if (!layer.supportsTextElevation) return
        if (!layer.appearance.appliesTextElevation()) return
        val appearance = layer.appearance
        val depthPx = appearance.resolvedDepthSizePx(renderScale)
        if (depthPx <= 0.5f) return

        if (layer.textForm.isActive) {
            drawWarpedExtrusion(
                canvas = canvas,
                layer = layer,
                left = left,
                top = top,
                width = width,
                height = height,
                renderScale = renderScale,
                context = context,
                alpha = alpha,
                depthPx = depthPx,
            )
            return
        }

        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        if (displayText.isBlank()) return

        val metrics = context.resources.displayMetrics
        val fontScale = metrics.scaledDensity / metrics.density
        val textSizePx = layer.textSizeSp * fontScale * renderScale
        val layoutWidth = textLayoutWidth?.toInt()?.coerceAtLeast(1) ?: width.toInt().coerceAtLeast(1)
        val lineSpacing = EditorTextStyleMapper.resolveLineSpacingMultiplier(layer.lineHeight)
        val alignment = EditorTextStyleMapper.resolveLayoutAlignment(layer.textAlign)
        val translateX = left
        val translateY = top

        val referencePaint = buildFillPaint(layer, textSizePx, alpha, context)
        drawExtrusionBehindLayout(
            canvas = canvas,
            displayText = displayText,
            translateX = translateX,
            translateY = translateY,
            textWidth = layoutWidth,
            alignment = alignment,
            lineSpacing = lineSpacing,
            layer = layer,
            renderScale = renderScale,
            context = context,
            textSizePx = textSizePx,
            alpha = alpha,
            referencePaint = referencePaint,
        )
    }

    private fun drawWarpedExtrusion(
        canvas: Canvas,
        layer: EditorLayer,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        renderScale: Float,
        context: Context,
        alpha: Int,
        depthPx: Float,
    ) {
        val appearance = layer.appearance
        val glyphs = TextFormLayoutEngine.computeLayerGlyphs(
            layer = layer,
            boxWidth = width,
            boxHeight = height,
            renderScale = renderScale,
            context = context,
        )
        if (glyphs.isEmpty()) return

        val metrics = context.resources.displayMetrics
        val fontScale = metrics.scaledDensity / metrics.density
        val textSizePx = layer.textSizeSp * fontScale * renderScale
        val angleRad = Math.toRadians(appearance.resolvedExtrusionAngleDeg().toDouble())
        val dx = (cos(angleRad) * depthPx).toFloat()
        val dy = (sin(angleRad) * depthPx).toFloat()

        val faceColor = layer.resolveTextElevationColorArgb()
        val sideColor = resolveShapeDepthColor(faceColor, appearance.depthColorArgb)
        val backColor = resolveShapeDepthBackColor(sideColor)

        appearance.depthShadowBlurPx(renderScale)?.takeIf { it > 0.5f }?.let { blurPx ->
            val shadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = sideColor
                this.alpha = (alpha * 0.55f).toInt().coerceIn(0, 255)
                setSafeBlurMaskFilter(blurPx)
                textSize = textSizePx
                textAlign = Align.CENTER
            }
            configureTypeface(layer, context, shadowPaint)
            canvas.save()
            canvas.translate(left, top)
            glyphs.forEach { spec ->
                canvas.save()
                canvas.translate(spec.x + dx, spec.y + dy)
                canvas.rotate(spec.rotationDeg)
                canvas.drawText(spec.char, 0f, 0f, shadowPaint)
                canvas.restore()
            }
            canvas.restore()
        }

        val steps = max((depthPx / 0.8f).toInt(), 12).coerceAtMost(64)
        val extrusionPaint = buildFillPaint(layer, textSizePx, alpha, context).apply {
            textAlign = Align.CENTER
        }

        canvas.save()
        canvas.translate(left, top)
        for (step in steps downTo 1) {
            val t = step.toFloat() / steps
            extrusionPaint.color = blendArgb(backColor, sideColor, t)
            glyphs.forEach { spec ->
                canvas.save()
                canvas.translate(spec.x + dx * t, spec.y + dy * t)
                canvas.rotate(spec.rotationDeg)
                canvas.drawText(spec.char, 0f, 0f, extrusionPaint)
                canvas.restore()
            }
        }
        canvas.restore()
    }

    private fun drawSoftTextShadow(
        canvas: Canvas,
        displayText: String,
        translateX: Float,
        translateY: Float,
        dx: Float,
        dy: Float,
        textWidth: Int,
        textAlign: String?,
        lineHeight: Float?,
        referencePaint: TextPaint,
        color: Int,
        alpha: Int,
        blurPx: Float,
    ) {
        val shadowPaint = TextPaint(referencePaint).apply {
            style = Paint.Style.FILL
            this.color = color
            this.alpha = alpha
            shader = null
            maskFilter = null
            setSafeBlurMaskFilter(blurPx)
        }
        val shadowLayout = createTextLayout(
            text = displayText,
            paint = shadowPaint,
            maxWidth = textWidth,
            textAlign = textAlign,
            lineHeight = lineHeight,
        )
        canvas.save()
        canvas.translate(translateX + dx, translateY + dy)
        shadowLayout.draw(canvas)
        canvas.restore()
    }

    private fun buildFillPaint(
        layer: EditorLayer,
        textSizePx: Float,
        alpha: Int,
        context: Context,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = layer.resolveTextElevationColorArgb()
        this.alpha = alpha
        textSize = textSizePx
        style = Paint.Style.FILL
        letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
        textAlign = Paint.Align.LEFT
        configureTypeface(layer, context, this)
    }

    private fun configureTypeface(layer: EditorLayer, context: Context, paint: TextPaint) {
        val customTf = layer.fontFamily?.let { family ->
            kotlinx.coroutines.runBlocking {
                FontDownloader.getTypeface(context, family)
            }
        }
        EditorTextStyleMapper.configureTextPaint(
            paint = paint,
            fontWeight = layer.fontWeight,
            fontStyle = layer.fontStyle,
            underline = layer.underline,
            linethrough = layer.linethrough,
            baseTypeface = customTf,
        )
    }

    private fun createTextLayout(
        text: String,
        paint: TextPaint,
        maxWidth: Int,
        textAlign: String?,
        lineHeight: Float?,
    ): Layout {
        val spacingMult = EditorTextStyleMapper.resolveLineSpacingMultiplier(lineHeight)
        val alignment = EditorTextStyleMapper.resolveLayoutAlignment(textAlign)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, spacingMult)
                .setIncludePad(TEXT_LAYOUT_INCLUDE_PAD)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, maxWidth, alignment, spacingMult, 0f, false)
        }
    }

    private fun blendArgb(from: Int, to: Int, t: Float): Int {
        val ratio = t.coerceIn(0f, 1f)
        fun ch(value: Int, shift: Int) = (value shr shift) and 0xFF
        val a = (ch(from, 24) + (ch(to, 24) - ch(from, 24)) * ratio).toInt().coerceIn(0, 255)
        val r = (ch(from, 16) + (ch(to, 16) - ch(from, 16)) * ratio).toInt().coerceIn(0, 255)
        val g = (ch(from, 8) + (ch(to, 8) - ch(from, 8)) * ratio).toInt().coerceIn(0, 255)
        val b = (ch(from, 0) + (ch(to, 0) - ch(from, 0)) * ratio).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
