package com.thgiang.image.studio.ui.editor.mapper

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Path
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object TextElevationMapper {

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

        val textSizePx = layer.textSizeSp * renderScale

        val fillPaint = buildFillPaint(layer, textSizePx, alpha, context)
        val measuredLayout = createTextLayout(
            displayText,
            fillPaint,
            Int.MAX_VALUE / 4,
            layer.textAlign,
            layer.lineHeight,
        )
        val intrinsicWidth = (0 until measuredLayout.lineCount)
            .maxOf { measuredLayout.getLineWidth(it) }
            .let { ceil(it).toInt() }
            .coerceAtLeast(1)
        val layoutWidth = textLayoutWidth?.toInt()?.coerceAtLeast(1) ?: intrinsicWidth
        val layoutHeight = textLayoutHeight?.toInt()?.coerceAtLeast(1) ?: measuredLayout.height

        val layout = if (layoutWidth == intrinsicWidth) {
            measuredLayout
        } else {
            createTextLayout(displayText, fillPaint, layoutWidth, layer.textAlign, layer.lineHeight)
        }

        val translateX = left + (width - layoutWidth) / 2f
        val translateY = top + (height - layoutHeight) / 2f

        val angleRad = Math.toRadians(appearance.resolvedExtrusionAngleDeg().toDouble())
        val dx = (cos(angleRad) * depthPx).toFloat()
        val dy = (sin(angleRad) * depthPx).toFloat()

        val faceColor = layer.resolveTextElevationColorArgb()
        val sideColor = resolveShapeDepthColor(faceColor, appearance.depthColorArgb)
        val backColor = resolveShapeDepthBackColor(sideColor)

        appearance.depthShadowBlurPx(renderScale)?.takeIf { it > 0.5f }?.let { blurPx ->
            drawSoftTextShadow(
                canvas = canvas,
                layout = layout,
                translateX = translateX,
                translateY = translateY,
                dx = dx,
                dy = dy,
                color = sideColor,
                alpha = (alpha * 0.55f).toInt().coerceIn(0, 255),
                blurPx = blurPx,
            )
        }

        val steps = max((depthPx / 1.5f).toInt(), 8).coerceAtMost(48)
        val extrusionPaint = buildFillPaint(layer, textSizePx, alpha, context)
        val extrusionLayout = createTextLayout(
            displayText,
            extrusionPaint,
            layoutWidth,
            layer.textAlign,
            layer.lineHeight,
        )

        for (step in steps downTo 1) {
            val t = step.toFloat() / steps
            extrusionPaint.color = blendArgb(backColor, sideColor, t)
            canvas.save()
            canvas.translate(translateX + dx * t, translateY + dy * t)
            extrusionLayout.draw(canvas)
            canvas.restore()
        }
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

        val textSizePx = layer.textSizeSp * renderScale
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
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
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
        layout: Layout,
        translateX: Float,
        translateY: Float,
        dx: Float,
        dy: Float,
        color: Int,
        alpha: Int,
        blurPx: Float,
    ) {
        val path = Path()
        layout.getSelectionPath(0, layout.text.length, path)
        path.offset(translateX + dx, translateY + dy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            this.alpha = alpha
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, paint)
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
                .setIncludePad(false)
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
