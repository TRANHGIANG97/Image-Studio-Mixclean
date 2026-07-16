package com.thgiang.image.studio.ui.editor.mapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.appliesTextElevation
import com.thgiang.image.studio.ui.editor.model.opaqueShadowColorArgb
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

    private data class GradientBounds(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )

    /** Gradient must span the layout box (StaticLayout aligns lines within this width). */
    private fun flatTextGradientBounds(
        layoutLeft: Float,
        textTop: Float,
        textWidth: Int,
        textHeight: Float,
    ): GradientBounds = GradientBounds(
        left = layoutLeft,
        top = textTop,
        width = textWidth.toFloat().coerceAtLeast(1f),
        height = textHeight.coerceAtLeast(1f),
    )

    private fun applyTextGradientShader(
        fillPaint: TextPaint,
        layer: EditorLayer,
        bounds: GradientBounds,
    ) {
        EditorGradientMapper.toAndroidShader(
            layer.textColorGradient,
            bounds.left,
            bounds.top,
            bounds.width,
            bounds.height,
            layer.textColorArgb,
        )?.let { fillPaint.shader = it }
    }

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

        // StaticLayout handles per-line alignment; paint anchor must stay LEFT.
        val align = Paint.Align.LEFT

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
            val shadowColor = layer.appearance.opaqueShadowColorArgb()
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

                setSafeBlurMaskFilter(blurRadius)

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

        val paddingH = 0f
        val textWidth = width.toInt().coerceAtLeast(1)
        val lineSpacing = EditorTextStyleMapper.resolveLineSpacingMultiplier(layer.lineHeight)
        val alignment = EditorTextStyleMapper.resolveLayoutAlignment(layer.textAlign)
        // Layout origin is always the left edge; StaticLayout aligns each line within textWidth.
        val translateX = left + paddingH

        val displayText: CharSequence = buildDisplayText(layer, context)
        val textTop = top

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

        @Suppress("DEPRECATION")
        val measureLayout = StaticLayout(
            displayText,
            paints.fillPaint,
            textWidth,
            alignment,
            lineSpacing,
            0f,
            TEXT_LAYOUT_INCLUDE_PAD,
        )
        if (layer.textColorGradient != null) {
            val gradientBounds = flatTextGradientBounds(
                layoutLeft = translateX,
                textTop = textTop,
                textWidth = textWidth,
                textHeight = measureLayout.height.toFloat(),
            )
            applyTextGradientShader(paints.fillPaint, layer, gradientBounds)
        }

        // --- 0. 3D extrusion (same layout metrics as fill pass) ---
        if (
            layer.supportsTextElevation &&
            layer.appearance.appliesTextElevation() &&
            !layer.textForm.isActive
        ) {
            TextElevationMapper.drawExtrusionBehindLayout(
                canvas = canvas,
                displayText = displayText.toString(),
                translateX = translateX,
                translateY = textTop,
                textWidth = textWidth,
                alignment = alignment,
                lineSpacing = lineSpacing,
                layer = layer,
                renderScale = renderScale,
                context = context,
                textSizePx = textSizePx,
                alpha = alpha,
                referencePaint = paints.fillPaint,
            )
        }

        // --- 1. Background Color pass (lowest layer, below text, shadow and stroke) ---
        val fillLayout = measureLayout

        if (layer.textBackgroundColorArgb != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layer.textBackgroundColorArgb
                style = Paint.Style.FILL
                this.alpha = alpha
            }
            val textHeight = fillLayout.height.toFloat()
            val localLeft = 0f
            val localRight = textWidth.toFloat()
            canvas.save()
            canvas.translate(translateX, textTop)
            canvas.drawRect(localLeft, 0f, localRight, textHeight, bgPaint)
            canvas.restore()
        }

        // --- 2. Shadow pass (glyph outlines via StaticLayout.draw + BlurMaskFilter) ---
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
            drawTextShadowBehindLayout(
                canvas = canvas,
                layout = shadowLayout,
                translateX = translateX,
                translateY = textTop,
                shadowDx = shadowDx,
                shadowDy = shadowDy,
            )
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

        var resolvedGradientLeft = gradientLeft
        var resolvedGradientTop = gradientTop
        var resolvedGradientWidth = gradientWidth
        var resolvedGradientHeight = gradientHeight
        if (layer.textColorGradient != null) {
            TextFormLayoutEngine.glyphContentBounds(glyphs, textSizePx)?.let { bounds ->
                resolvedGradientLeft = left + bounds.left
                resolvedGradientTop = top + bounds.top
                resolvedGradientWidth = bounds.width().coerceAtLeast(1f)
                resolvedGradientHeight = bounds.height().coerceAtLeast(1f)
            }
        }

        val paints = buildTextPaints(
            layer = layer,
            textSizePx = textSizePx,
            alpha = alpha,
            context = context,
            renderScale = renderScale,
            gradientLeft = resolvedGradientLeft,
            gradientTop = resolvedGradientTop,
            gradientWidth = resolvedGradientWidth,
            gradientHeight = resolvedGradientHeight,
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

    /**
     * Draw soft drop shadow on glyph outlines (same layout metrics as fill pass).
     * [Layout.getSelectionPath] builds per-line selection rectangles (full line width), not glyph shapes.
     */
    private fun drawTextShadowBehindLayout(
        canvas: Canvas,
        layout: Layout,
        translateX: Float,
        translateY: Float,
        shadowDx: Float,
        shadowDy: Float,
    ) {
        if (layout.text.isEmpty()) return
        canvas.save()
        canvas.translate(translateX + shadowDx, translateY + shadowDy)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildDisplayText(layer: EditorLayer, context: Context): CharSequence {
        val spans = TextRunOps.effectiveSpans(layer)
        if (spans.size <= 1) {
            return EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        }
        // Length-preserving transforms can keep per-run styles; otherwise fall back to flat.
        val transformedRuns = spans.map { span ->
            span.copy(text = EditorTextStyleMapper.applyTextTransform(span.text, layer.textTransform))
        }
        val joined = transformedRuns.joinToString("") { it.text }
        if (joined.length != layer.text.length && layer.textTransform != null) {
            // Unsafe to map styles (e.g. some capitalize edge cases) — flat text.
            return EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        }
        val typefaceCache = mutableMapOf<String, android.graphics.Typeface?>()
        fun resolveTypeface(family: String?): android.graphics.Typeface? {
            if (family.isNullOrBlank()) return null
            return typefaceCache.getOrPut(family) {
                runBlocking { FontDownloader.getTypeface(context, family) }
            }
        }
        val builder = SpannableStringBuilder()
        transformedRuns.forEach { span ->
            val start = builder.length
            builder.append(span.text)
            val end = builder.length
            val weight = span.fontWeight ?: layer.fontWeight
            val style = span.fontStyle ?: layer.fontStyle
            val typefaceStyle = when {
                EditorTextStyleMapper.isBoldWeight(weight) && EditorTextStyleMapper.isItalicStyle(style) ->
                    android.graphics.Typeface.BOLD_ITALIC
                EditorTextStyleMapper.isBoldWeight(weight) -> android.graphics.Typeface.BOLD
                EditorTextStyleMapper.isItalicStyle(style) -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            }
            val family = span.fontFamily ?: layer.fontFamily
            val customTf = resolveTypeface(family)
            if (customTf != null) {
                val styled = android.graphics.Typeface.create(customTf, typefaceStyle)
                builder.setSpan(CustomTypefaceSpan(styled), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (typefaceStyle != android.graphics.Typeface.NORMAL) {
                builder.setSpan(StyleSpan(typefaceStyle), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val color = span.colorArgb ?: layer.textColorArgb
            builder.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (span.underline ?: layer.underline) {
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (span.linethrough ?: layer.linethrough) {
                builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    /** minSdk 24-safe Typeface span (API 28+ TypefaceSpan(Typeface) is unavailable). */
    private class CustomTypefaceSpan(
        private val typeface: android.graphics.Typeface,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.typeface = typeface
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.typeface = typeface
        }
    }
}
