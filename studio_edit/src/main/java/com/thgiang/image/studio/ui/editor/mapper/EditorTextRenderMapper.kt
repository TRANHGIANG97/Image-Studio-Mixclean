package com.thgiang.image.studio.ui.editor.mapper

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.resolvedShadowBlurRadius
import com.thgiang.image.studio.ui.editor.model.shadowOpacityFromIntensity
import com.thgiang.image.studio.util.FontDownloader
import kotlinx.coroutines.runBlocking

data class TextPaintTriple(
    val fillPaint: TextPaint,
    val strokePaint: TextPaint?,
    val shadowPaint: TextPaint?,
)

object EditorTextRenderMapper {

    /** Must match [ShapeTextBoundsResolver] layout metrics (includePad = false). */
    private const val TEXT_LAYOUT_INCLUDE_PAD = false

    fun buildTextPaints(
        layer: EditorLayer,
        textSizePx: Float,
        alpha: Int,
        context: Context,
        renderScale: Float,
        gradientLeft: Float,
        gradientTop: Float,
        gradientWidth: Float,
        gradientHeight: Float,
    ): TextPaintTriple {
        val customTf = layer.fontFamily?.let { familyName ->
            runBlocking {
                FontDownloader.getTypeface(context, familyName)
            }
        }

        val align = when (EditorTextStyleMapper.resolveLayoutAlignment(layer.textAlign)) {
            android.text.Layout.Alignment.ALIGN_NORMAL -> Paint.Align.LEFT
            android.text.Layout.Alignment.ALIGN_OPPOSITE -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }

        // 1. Fill Paint
        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.textColorArgb
            this.alpha = alpha
            textSize = textSizePx
            style = Paint.Style.FILL
            letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
            textAlign = align

            if (customTf != null) {
                EditorTextStyleMapper.configureTextPaint(
                    paint = this,
                    fontWeight = layer.fontWeight,
                    fontStyle = layer.fontStyle,
                    underline = layer.underline,
                    linethrough = layer.linethrough,
                    baseTypeface = customTf,
                )
            } else {
                EditorTextStyleMapper.configureTextPaint(
                    paint = this,
                    fontWeight = layer.fontWeight,
                    fontStyle = layer.fontStyle,
                    underline = layer.underline,
                    linethrough = layer.linethrough,
                )
            }

            EditorGradientMapper.toAndroidShader(
                layer.textColorGradient,
                gradientLeft,
                gradientTop,
                gradientWidth,
                gradientHeight,
                layer.textColorArgb,
            )?.let { shader = it }
        }

        // 2. Stroke Paint
        val strokePaint = if (layer.strokeWidthPx > 0f) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layer.strokeColorArgb ?: layer.textColorArgb
                this.alpha = alpha
                textSize = textSizePx
                style = Paint.Style.STROKE
                strokeWidth = layer.strokeWidthPx * renderScale
                letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
                textAlign = align

                if (customTf != null) {
                    EditorTextStyleMapper.configureTextPaint(
                        paint = this,
                        fontWeight = layer.fontWeight,
                        fontStyle = layer.fontStyle,
                        underline = layer.underline,
                        linethrough = layer.linethrough,
                        baseTypeface = customTf,
                    )
                } else {
                    EditorTextStyleMapper.configureTextPaint(
                        paint = this,
                        fontWeight = layer.fontWeight,
                        fontStyle = layer.fontStyle,
                        underline = layer.underline,
                        linethrough = layer.linethrough,
                    )
                }
            }
        } else {
            null
        }

        // 3. Shadow Paint
        val shadowPaint = if (layer.appearance.shadowIntensity > 0.05f) {
            val shadowColor = layer.appearance.shadowColorArgb
            val shadowAlpha = (
                shadowOpacityFromIntensity(layer.appearance.shadowIntensity) *
                    layer.appearance.alpha *
                    (alpha / 255f) *
                    255f
                ).toInt().coerceIn(0, 255)

            val blurRadius = layer.appearance.resolvedShadowBlurRadius() * renderScale.coerceAtLeast(0.01f)

            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shadowColor
                this.alpha = shadowAlpha
                textSize = textSizePx
                style = Paint.Style.FILL
                letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
                textAlign = align

                if (blurRadius > 0.1f) {
                    maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                }

                if (customTf != null) {
                    EditorTextStyleMapper.configureTextPaint(
                        paint = this,
                        fontWeight = layer.fontWeight,
                        fontStyle = layer.fontStyle,
                        underline = layer.underline,
                        linethrough = layer.linethrough,
                        baseTypeface = customTf,
                    )
                } else {
                    EditorTextStyleMapper.configureTextPaint(
                        paint = this,
                        fontWeight = layer.fontWeight,
                        fontStyle = layer.fontStyle,
                        underline = layer.underline,
                        linethrough = layer.linethrough,
                    )
                }
            }
        } else {
            null
        }

        return TextPaintTriple(fillPaint, strokePaint, shadowPaint)
    }

    fun drawFlatTextOnCanvas(
        canvas: Canvas,
        layer: EditorLayer,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        renderScale: Float,
        context: Context,
        alpha: Int = (layer.appearance.alpha * 255f).toInt().coerceIn(0, 255),
        rotationDeg: Float = 0f,
        gradientLeft: Float = left,
        gradientTop: Float = top,
        gradientWidth: Float = width,
        gradientHeight: Float = height,
    ) {
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.displayMetrics.scaledDensity / density
        val textSizePx = layer.textSizeSp * fontScale * renderScale

        val paints = buildTextPaints(
            layer = layer,
            textSizePx = textSizePx,
            alpha = alpha,
            context = context,
            renderScale = renderScale,
            gradientLeft = gradientLeft,
            gradientTop = gradientTop,
            gradientWidth = gradientWidth,
            gradientHeight = gradientHeight,
        )

        val paddingH = 0f
        val textWidth = width.toInt().coerceAtLeast(1)
        val lineSpacing = EditorTextStyleMapper.resolveLineSpacingMultiplier(layer.lineHeight)
        val alignment = EditorTextStyleMapper.resolveLayoutAlignment(layer.textAlign)
        val translateX = when (alignment) {
            android.text.Layout.Alignment.ALIGN_NORMAL -> left + paddingH
            android.text.Layout.Alignment.ALIGN_OPPOSITE -> left + paddingH + textWidth
            else -> left + paddingH + textWidth / 2f
        }

        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)

        // --- 1. Background Color pass (lowest layer, below text, shadow and stroke) ---
        @Suppress("DEPRECATION")
        val fillLayout = StaticLayout(
            displayText,
            paints.fillPaint,
            textWidth,
            alignment,
            lineSpacing,
            0f,
            TEXT_LAYOUT_INCLUDE_PAD,
        )
        // Top-aligned to match Compose TextLabelLayerContent (Alignment.TopCenter).
        val textTop = top

        if (layer.textBackgroundColorArgb != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layer.textBackgroundColorArgb
                style = Paint.Style.FILL
                this.alpha = alpha
            }
            val textHeight = fillLayout.height.toFloat()
            val localLeft = when (alignment) {
                android.text.Layout.Alignment.ALIGN_NORMAL -> 0f
                android.text.Layout.Alignment.ALIGN_OPPOSITE -> -textWidth.toFloat()
                else -> -textWidth / 2f
            }
            val localRight = localLeft + textWidth
            canvas.save()
            canvas.translate(translateX, textTop)
            canvas.drawRect(localLeft, 0f, localRight, textHeight, bgPaint)
            canvas.restore()
        }

        // --- 2. Shadow pass ---
        paints.shadowPaint?.let { shadowPaint ->
            val (shadowDx, shadowDy) = EditorShadowMapper.shadowOffsetLocalPx(
                layer.appearance,
                renderScale,
                rotationDeg,
            )
            @Suppress("DEPRECATION")
            val shadowLayout = StaticLayout(
                displayText,
                shadowPaint,
                textWidth,
                alignment,
                lineSpacing,
                0f,
                TEXT_LAYOUT_INCLUDE_PAD,
            )
            canvas.save()
            canvas.translate(translateX + shadowDx, textTop + shadowDy)
            shadowLayout.draw(canvas)
            canvas.restore()
        }

        // --- 3. Stroke pass ---
        paints.strokePaint?.let { strokePaint ->
            @Suppress("DEPRECATION")
            val strokeLayout = StaticLayout(
                displayText,
                strokePaint,
                textWidth,
                alignment,
                lineSpacing,
                0f,
                TEXT_LAYOUT_INCLUDE_PAD,
            )
            canvas.save()
            canvas.translate(translateX, textTop)
            strokeLayout.draw(canvas)
            canvas.restore()
        }

        // --- 4. Fill pass ---
        canvas.save()
        canvas.translate(translateX, textTop)
        fillLayout.draw(canvas)
        canvas.restore()
    }

    fun drawGlyphsOnCanvas(
        canvas: Canvas,
        glyphs: List<GlyphDrawSpec>,
        layer: EditorLayer,
        left: Float,
        top: Float,
        renderScale: Float,
        context: Context,
        alpha: Int = (layer.appearance.alpha * 255f).toInt().coerceIn(0, 255),
        rotationDeg: Float = 0f,
        gradientLeft: Float = left,
        gradientTop: Float = top,
        gradientWidth: Float = canvas.width.toFloat(),
        gradientHeight: Float = canvas.height.toFloat(),
    ) {
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.displayMetrics.scaledDensity / density
        val textSizePx = layer.textSizeSp * fontScale * renderScale
        val paints = buildTextPaints(
            layer = layer,
            textSizePx = textSizePx,
            alpha = alpha,
            context = context,
            renderScale = renderScale,
            gradientLeft = gradientLeft,
            gradientTop = gradientTop,
            gradientWidth = gradientWidth,
            gradientHeight = gradientHeight,
        )

        paints.fillPaint.textAlign = Paint.Align.CENTER
        paints.strokePaint?.textAlign = Paint.Align.CENTER
        paints.shadowPaint?.textAlign = Paint.Align.CENTER

        canvas.save()
        canvas.translate(left, top)

        // --- 1. Shadow pass ---
        paints.shadowPaint?.let { shadowPaint ->
            val (shadowDx, shadowDy) = EditorShadowMapper.shadowOffsetLocalPx(
                layer.appearance,
                renderScale,
                rotationDeg,
            )
            glyphs.forEach { spec ->
                canvas.save()
                canvas.translate(spec.x + shadowDx, spec.y + shadowDy)
                canvas.rotate(spec.rotationDeg)
                canvas.drawText(spec.char, 0f, 0f, shadowPaint)
                canvas.restore()
            }
        }

        // --- 2. Stroke pass ---
        paints.strokePaint?.let { strokePaint ->
            glyphs.forEach { spec ->
                canvas.save()
                canvas.translate(spec.x, spec.y)
                canvas.rotate(spec.rotationDeg)
                canvas.drawText(spec.char, 0f, 0f, strokePaint)
                canvas.restore()
            }
        }

        // --- 3. Fill pass ---
        glyphs.forEach { spec ->
            canvas.save()
            canvas.translate(spec.x, spec.y)
            canvas.rotate(spec.rotationDeg)
            canvas.drawText(spec.char, 0f, 0f, paints.fillPaint)
            canvas.restore()
        }

        canvas.restore()
    }
}
