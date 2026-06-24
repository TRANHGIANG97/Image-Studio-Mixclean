package com.thgiang.image.studio.ui.editor.label.factory
import com.thgiang.image.studio.ui.editor.label.factory.*
import com.thgiang.image.studio.ui.editor.label.model.*
import com.thgiang.image.studio.ui.editor.canvas.*

import com.thgiang.image.studio.ui.editor.model.*

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorLayerFactory @Inject constructor() {
    fun createShapeTextLayer(templateWidth: Float, shapeType: ShapeType): EditorLayer {
        val defaultW = (if (templateWidth > 0f) templateWidth * 0.20f else 120f).coerceIn(60f, 300f)
        val defaultH = (defaultW * 0.42f).coerceIn(30f, 110f)
        val lineStrokeColor = 0xFF424242.toInt()
        val isLine = shapeType == ShapeType.LINE
        val isTextOnly = shapeType == ShapeType.TEXT_ONLY

        val baseLayer = EditorLayer(
            type = LayerType.SHAPE_TEXT,
            text = "Label",
            textColorArgb = 0xFF000000.toInt(),
            textSizeSp = 28f,
            shapeType = shapeType,
            shapeColorArgb = 0x00FFFFFF,
            shapeWidthPx = defaultW,
            shapeHeightPx = defaultH,
            cornerRadiusX = if (shapeType == ShapeType.CARD) 0f else null,
            cornerRadiusY = if (shapeType == ShapeType.CARD) 0f else null,
            strokeColorArgb = when {
                isLine -> lineStrokeColor
                isTextOnly -> null
                else -> ShapeLabelDefaults.BORDER_COLOR_ARGB
            },
            strokeWidthPx = when {
                isLine -> 6f
                isTextOnly -> 0f
                else -> ShapeLabelDefaults.BORDER_WIDTH_PX
            },
            viewport = EditorViewport(scale = 1f),
            appearance = EditorAppearance(shadowIntensity = 0f, alpha = 1f),
        )

        return baseLayer
    }

    /**
     * Create a decorative shape layer (no text, no auto-fitting).
     * Independent from the Label feature.
     * Mirrors [createShapeTextLayer] defaults exactly, but with empty text.
     */
    fun createShapeLayer(templateWidth: Float, shapeType: ShapeType): EditorLayer {
        val defaultW = (if (templateWidth > 0f) templateWidth * 0.20f else 120f).coerceIn(60f, 300f)
        val defaultH = (defaultW * 0.42f).coerceIn(30f, 110f)
        val lineStrokeColor = 0xFF424242.toInt()
        val isLine = shapeType == ShapeType.LINE
        val isTextOnly = shapeType == ShapeType.TEXT_ONLY

        return EditorLayer(
            type = LayerType.SHAPE_TEXT,
            text = "",                          // No text — shape only
            textColorArgb = 0xFF000000.toInt(),
            textSizeSp = 28f,
            shapeType = shapeType,
            shapeColorArgb = 0x00FFFFFF,        // Transparent fill (same as old label shapes)
            shapeWidthPx = defaultW,
            shapeHeightPx = defaultH,
            cornerRadiusX = if (shapeType == ShapeType.CARD) 0f else null,
            cornerRadiusY = if (shapeType == ShapeType.CARD) 0f else null,
            strokeColorArgb = when {
                isLine -> lineStrokeColor
                isTextOnly -> null
                else -> ShapeLabelDefaults.BORDER_COLOR_ARGB
            },
            strokeWidthPx = when {
                isLine -> 6f
                isTextOnly -> 0f
                else -> ShapeLabelDefaults.BORDER_WIDTH_PX
            },
            viewport = EditorViewport(scale = 1f),
            appearance = EditorAppearance(shadowIntensity = 0f, alpha = 1f),
        )
    }

    fun createTextLayer(templateWidth: Float): EditorLayer {
        val defaultW = (if (templateWidth > 0f) templateWidth * 0.58f else 360f).coerceIn(180f, 760f)
        val defaultH = (defaultW * 0.24f).coerceIn(72f, 220f)

        return EditorLayer(
            type = LayerType.SHAPE_TEXT,
            text = "Nhập chữ...",
            textColorArgb = 0xFF5B21B6.toInt(),
            textSizeSp = 28f,
            shapeType = ShapeType.TEXT_ONLY,
            shapeColorArgb = 0x00FFFFFF,
            shapeWidthPx = defaultW,
            shapeHeightPx = defaultH,
            fontFamily = "sans-serif",
            viewport = EditorViewport(scale = 1f),
            appearance = EditorAppearance(shadowIntensity = 0f, alpha = 1f),
        )
    }

    fun createStickerLayer(
        stickerPath: String,
        stickerWidth: Int,
        stickerHeight: Int,
        initialScale: Float,
    ): EditorLayer = EditorLayer(
        product = EditorProduct(
            originalUriString = stickerPath,
            foregroundUriString = stickerPath,
            isBackgroundRemoved = true,
            baseWidth = stickerWidth,
            baseHeight = stickerHeight,
            processing = false,
            isSample = false,
        ),
        viewport = EditorViewport(scale = initialScale),
        appearance = EditorAppearance(
            shadowIntensity = 0f,
            alpha = 1f,
            shadowAngle = 45f,
            shadowDistance = 12f,
            shadowColorArgb = 0xFF000000.toInt(),
        ),
        cropRatio = CropRatio.ORIGINAL,
    )
}
