package com.thgiang.image.studio.ui.editor.label.model
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.label.model.*
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

object ShapeTextBoundsResolver {
    private const val PADDING_H_DP = 12f
    private const val PADDING_V_DP = 6f
    private const val MIN_WIDTH_PX = 60f
    private const val MIN_HEIGHT_PX = 30f

    fun fitShapeToText(layer: EditorLayer, context: Context): EditorLayer {
        if (layer.type != LayerType.SHAPE_TEXT) return layer
        if (EditorShapeGeometry.isTextOnlyShape(layer.shapeType)) return layer

        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.displayMetrics.scaledDensity / density
        val textSizePx = layer.textSizeSp * fontScale
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)

        if (displayText.isBlank()) return layer

        val paint = buildTextPaint(layer, textSizePx)
        val layout = createSingleLineLayout(displayText, paint, layer.lineHeight)

        var textWidth = 0f
        for (line in 0 until layout.lineCount) {
            textWidth = max(textWidth, layout.getLineWidth(line))
        }
        val textHeight = layout.height.toFloat()

        val paddingH = PADDING_H_DP * density
        val paddingV = PADDING_V_DP * density
        val strokePad = shapeStrokePadding(layer)

        return if (EditorShapeGeometry.isLineShape(layer.shapeType)) {
            layer.copy(
                shapeWidthPx = (textWidth + 2f * paddingH + strokePad).coerceAtLeast(MIN_WIDTH_PX),
            )
        } else {
            layer.copy(
                shapeWidthPx = (textWidth + 2f * paddingH + strokePad).coerceAtLeast(MIN_WIDTH_PX),
                shapeHeightPx = (textHeight + 2f * paddingV + strokePad).coerceAtLeast(MIN_HEIGHT_PX),
            )
        }
    }

    private fun shapeStrokePadding(layer: EditorLayer): Float {
        if (!layer.hasShapeBorder) return 0f
        return layer.resolveStrokeWidthPx() * 2f
    }

    private fun buildTextPaint(layer: EditorLayer, textSizePx: Float): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
            EditorTextStyleMapper.configureTextPaint(
                paint = this,
                fontWeight = layer.fontWeight,
                fontStyle = layer.fontStyle,
                underline = layer.underline,
                linethrough = layer.linethrough,
            )
        }
    }

    private fun createSingleLineLayout(
        text: String,
        paint: TextPaint,
        lineHeight: Float?,
    ): Layout {
        val spacingMult = EditorTextStyleMapper.resolveLineSpacingMultiplier(lineHeight)
        val maxWidth = Int.MAX_VALUE / 4
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacingMult)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, spacingMult, 0f, false)
        }
    }
}

fun EditorLayer.withShapeFittedToText(context: Context): EditorLayer =
    ShapeTextBoundsResolver.fitShapeToText(this, context)
