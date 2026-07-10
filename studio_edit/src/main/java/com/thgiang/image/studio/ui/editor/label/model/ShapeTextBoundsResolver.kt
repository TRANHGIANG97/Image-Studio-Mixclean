package com.thgiang.image.studio.ui.editor.label.model
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.label.model.*
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.content.Context
import com.thgiang.image.studio.util.FontDownloader
import kotlinx.coroutines.runBlocking
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.max

object ShapeTextBoundsResolver {
    private const val PADDING_H_DP = 12f
    private const val PADDING_V_DP = 6f
    private const val MIN_WIDTH_PX = 60f
    private const val MIN_HEIGHT_PX = 30f

    /** Must match [EditorTextRenderMapper] StaticLayout includePad setting. */
    internal const val TEXT_LAYOUT_INCLUDE_PAD = false

    private data class FlatTextMetrics(
        val textWidth: Float,
        val textHeight: Float,
    )

    private data class TextFormAspectFactors(
        val widthMultiplier: Float,
        val heightMultiplier: Float,
        val minHeightFromWidthRatio: Float = 0f,
    )

    fun fitShapeToText(layer: EditorLayer, context: Context): EditorLayer {
        if (layer.type != LayerType.SHAPE && layer.type != LayerType.TEXT && layer.type != LayerType.SHAPE_TEXT) {
            return layer
        }
        if (layer.textForm.isActive) {
            return layer
        }

        val density = context.resources.displayMetrics.density
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)

        if (displayText.isBlank()) return layer

        val textSizePx = resolveTextSizePx(layer, context)
        val natural = measureFlatText(layer, displayText, textSizePx, context, maxLayoutWidth = Int.MAX_VALUE / 4)
        val wrapped = measureFlatText(
            layer,
            displayText,
            textSizePx,
            context,
            maxLayoutWidth = ceil(natural.textWidth).toInt().coerceAtLeast(1),
        )
        return applyFlatFit(layer, natural.textWidth, wrapped.textHeight, density)
    }

    /** Keeps [EditorLayer.shapeWidthPx]; recomputes height from wrapped text at current inner width. */
    fun fitShapePreservingWidth(layer: EditorLayer, context: Context): EditorLayer {
        if (layer.type != LayerType.SHAPE && layer.type != LayerType.TEXT && layer.type != LayerType.SHAPE_TEXT) {
            return layer
        }
        if (layer.textForm.isActive) return layer

        val density = context.resources.displayMetrics.density
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        if (displayText.isBlank()) return layer

        val textSizePx = resolveTextSizePx(layer, context)
        val innerW = contentInnerWidthPx(layer, density)
        val flat = measureFlatText(layer, displayText, textSizePx, context, maxLayoutWidth = innerW)
        return applyFlatFit(layer, flat.textWidth, flat.textHeight, density, preserveWidth = layer.shapeWidthPx)
    }

    fun fitShapeToTextForm(layer: EditorLayer, context: Context): EditorLayer {
        if (layer.type != LayerType.SHAPE && layer.type != LayerType.TEXT && layer.type != LayerType.SHAPE_TEXT) {
            return layer
        }
        if (!layer.textForm.isActive) {
            return fitShapeToText(layer, context)
        }

        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.displayMetrics.scaledDensity / density
        val textSizePx = resolveTextSizePx(layer, context)
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)

        if (displayText.isBlank()) return layer

        val flat = measureFlatText(layer, displayText, textSizePx, context, maxLayoutWidth = Int.MAX_VALUE / 4)
        val paddingH = PADDING_H_DP * density
        val paddingV = PADDING_V_DP * density
        val strokePad = shapeStrokePadding(layer)
        val bleedPad = shadowBleedPad(layer)
        val factors = textFormAspectFactors(layer.textForm.preset)

        var candidateW = (flat.textWidth * factors.widthMultiplier + 2f * paddingH + strokePad + 2f * bleedPad)
            .coerceAtLeast(MIN_WIDTH_PX)
        var candidateH = (flat.textHeight * factors.heightMultiplier + 2f * paddingV + strokePad + 2f * bleedPad)
            .coerceAtLeast(MIN_HEIGHT_PX)

        if (factors.minHeightFromWidthRatio > 0f) {
            val minH = flat.textWidth * factors.minHeightFromWidthRatio + 2f * paddingV + strokePad + 2f * bleedPad
            candidateH = max(candidateH, minH)
        }

        val extents = TextFormLayoutEngine.measureGlyphExtents(
            layer = layer,
            boxWidth = candidateW,
            boxHeight = candidateH,
            renderScale = fontScale,
            context = context,
        )
        if (extents != null) {
            candidateW = (extents.width() + 2f * paddingH + strokePad + 2f * bleedPad).coerceAtLeast(MIN_WIDTH_PX)
            candidateH = (extents.height() + 2f * paddingV + strokePad + 2f * bleedPad).coerceAtLeast(MIN_HEIGHT_PX)
        }

        return if (EditorShapeGeometry.isLineShape(layer.shapeType)) {
            layer.copy(shapeWidthPx = candidateW)
        } else {
            layer.copy(
                shapeWidthPx = candidateW,
                shapeHeightPx = candidateH,
            )
        }
    }

    private fun applyFlatFit(
        layer: EditorLayer,
        textWidth: Float,
        textHeight: Float,
        density: Float,
        preserveWidth: Float? = null,
    ): EditorLayer {
        val paddingH = if (layer.shouldRenderFrameContent) PADDING_H_DP * density else 0f
        val paddingV = if (layer.shouldRenderFrameContent) PADDING_V_DP * density else 0f
        val strokePad = shapeStrokePadding(layer)
        val bleedPad = shadowBleedPad(layer)
        val slackV = density

        if (EditorShapeGeometry.isTextOnlyShape(layer.shapeType)) {
            val fittedW = preserveWidth ?: (textWidth + 2f * paddingH + 2f * bleedPad).coerceAtLeast(MIN_WIDTH_PX)
            val fittedH = (textHeight + 2f * paddingV + slackV + 2f * bleedPad).coerceAtLeast(MIN_HEIGHT_PX)
            return layer.copy(
                shapeWidthPx = fittedW,
                shapeHeightPx = fittedH,
            )
        }

        val fittedW = preserveWidth ?: (textWidth + 2f * paddingH + strokePad + 2f * bleedPad).coerceAtLeast(MIN_WIDTH_PX)
        val fittedH = (textHeight + 2f * paddingV + strokePad + slackV + 2f * bleedPad).coerceAtLeast(MIN_HEIGHT_PX)

        return if (EditorShapeGeometry.isLineShape(layer.shapeType)) {
            layer.copy(shapeWidthPx = fittedW)
        } else {
            layer.copy(
                shapeWidthPx = fittedW,
                shapeHeightPx = fittedH,
            )
        }
    }

    private fun resolveTextSizePx(layer: EditorLayer, context: Context): Float {
        val metrics = context.resources.displayMetrics
        val fontScale = metrics.scaledDensity / metrics.density
        return layer.textSizeSp * fontScale * layer.viewport.scale
    }

    private fun contentInnerWidthPx(layer: EditorLayer, density: Float): Int {
        val paddingH = if (layer.shouldRenderFrameContent) PADDING_H_DP * density else 0f
        val strokePad = shapeStrokePadding(layer)
        return (layer.shapeWidthPx - 2f * paddingH - strokePad).coerceAtLeast(1f).toInt()
    }

    private fun measureFlatText(
        layer: EditorLayer,
        displayText: String,
        textSizePx: Float,
        context: Context,
        maxLayoutWidth: Int,
    ): FlatTextMetrics {
        val paint = buildTextPaint(layer, textSizePx, context)
        val layout = createTextLayout(
            text = displayText,
            paint = paint,
            lineHeight = layer.lineHeight,
            textAlign = layer.textAlign,
            maxWidth = maxLayoutWidth,
        )

        var textWidth = 0f
        for (line in 0 until layout.lineCount) {
            textWidth = max(textWidth, layout.getLineWidth(line))
        }
        val textHeight = if (layout.lineCount > 0) {
            (layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0)).toFloat()
        } else {
            0f
        }
        return FlatTextMetrics(
            textWidth = textWidth,
            textHeight = textHeight,
        )
    }

    private fun textFormAspectFactors(preset: TextFormPreset): TextFormAspectFactors = when (preset) {
        TextFormPreset.PATH_ARC_UP,
        TextFormPreset.PATH_ARC_DOWN ->
            TextFormAspectFactors(widthMultiplier = 1.08f, heightMultiplier = 0.55f, minHeightFromWidthRatio = 0.62f)
        TextFormPreset.PATH_CIRCLE,
        TextFormPreset.PATH_RING ->
            TextFormAspectFactors(widthMultiplier = 1.75f, heightMultiplier = 1.75f)
        TextFormPreset.PATH_WAVE ->
            TextFormAspectFactors(widthMultiplier = 1.15f, heightMultiplier = 1.65f)
        TextFormPreset.WARP_ARCH_UP,
        TextFormPreset.WARP_ARCH_DOWN ->
            TextFormAspectFactors(widthMultiplier = 1.12f, heightMultiplier = 2.75f)
        TextFormPreset.WARP_BULGE,
        TextFormPreset.WARP_INFLATE,
        TextFormPreset.WARP_DEFLATE ->
            TextFormAspectFactors(widthMultiplier = 1.12f, heightMultiplier = 2.2f)
        TextFormPreset.WARP_WAVE,
        TextFormPreset.WARP_FLAG ->
            TextFormAspectFactors(widthMultiplier = 1.15f, heightMultiplier = 2.0f)
        TextFormPreset.WARP_RISE,
        TextFormPreset.WARP_FALL,
        TextFormPreset.WARP_CHEVRON_UP,
        TextFormPreset.WARP_CHEVRON_DOWN ->
            TextFormAspectFactors(widthMultiplier = 1.1f, heightMultiplier = 2.35f)
        else ->
            TextFormAspectFactors(widthMultiplier = 1.12f, heightMultiplier = 2.0f)
    }

    private fun shapeStrokePadding(layer: EditorLayer): Float {
        if (!layer.hasShapeBorder) return 0f
        return layer.resolveStrokeWidthPx() * 2f
    }

    internal fun shadowBleedPad(layer: EditorLayer): Float {
        if (layer.appearance.shadowIntensity <= 0.05f) return 0f
        val strokePad = shapeStrokePadding(layer)
        return EditorShadowMapper.computeShadowBleedPx(
            appearance = layer.appearance,
            scale = layer.viewport.scale,
            rotationDeg = layer.viewport.rotation,
            extraStrokePx = strokePad,
        ) - strokePad
    }

    private fun buildTextPaint(layer: EditorLayer, textSizePx: Float, context: Context): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
            val baseTypeface = layer.fontFamily?.let { family ->
                runBlocking { FontDownloader.getTypeface(context, family) }
            }
            EditorTextStyleMapper.configureTextPaint(
                paint = this,
                fontWeight = layer.fontWeight,
                fontStyle = layer.fontStyle,
                underline = layer.underline,
                linethrough = layer.linethrough,
                baseTypeface = baseTypeface,
            )
        }
    }

    private fun createTextLayout(
        text: String,
        paint: TextPaint,
        lineHeight: Float?,
        textAlign: String?,
        maxWidth: Int,
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
            StaticLayout(text, paint, maxWidth, alignment, spacingMult, 0f, TEXT_LAYOUT_INCLUDE_PAD)
        }
    }
}

fun EditorLayer.withShapeFittedToText(context: Context): EditorLayer =
    ShapeTextBoundsResolver.fitShapeToText(this, context)

fun EditorLayer.withShapeHeightFittedToText(context: Context): EditorLayer =
    ShapeTextBoundsResolver.fitShapePreservingWidth(this, context)

fun EditorLayer.withTextFormShapeFitted(context: Context): EditorLayer =
    ShapeTextBoundsResolver.fitShapeToTextForm(this, context)
