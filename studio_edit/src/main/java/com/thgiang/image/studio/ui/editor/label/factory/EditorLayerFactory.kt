package com.thgiang.image.studio.ui.editor.label.factory
import com.thgiang.image.studio.ui.editor.label.factory.*
import com.thgiang.image.studio.ui.editor.label.model.*
import com.thgiang.image.studio.ui.editor.canvas.*

import com.thgiang.image.studio.ui.editor.model.*

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorLayerFactory @Inject constructor() {
    /** Phase 3: label on shape → frame + label group. TEXT_ONLY → single TEXT layer. */
    fun createShapeTextGroup(templateWidth: Float, shapeType: ShapeType): List<EditorLayer> {
        if (shapeType == ShapeType.TEXT_ONLY) {
            return listOf(createTextLayer(templateWidth).copy(text = "Label"))
        }
        return EditorLayerNormalizer.normalize(listOf(buildHybridShapeTextLayer(templateWidth, shapeType)))
    }

    private fun buildHybridShapeTextLayer(templateWidth: Float, shapeType: ShapeType): EditorLayer {
        val defaultW = (if (templateWidth > 0f) templateWidth * 0.20f else 120f).coerceIn(60f, 300f)
        val defaultH = (defaultW * 0.42f).coerceIn(30f, 110f)
        val lineStrokeColor = 0xFF424242.toInt()
        val isLine = shapeType == ShapeType.LINE

        return EditorLayer(
            type = LayerType.SHAPE_TEXT,
            text = "Label",
            textColorArgb = 0xFF000000.toInt(),
            textSizeSp = ShapeLabelDefaults.DEFAULT_TEXT_SIZE_SP,
            shapeType = shapeType,
            shapeColorArgb = if (isLine) 0x00FFFFFF else 0xFFE3F2FD.toInt(),
            shapeWidthPx = defaultW,
            shapeHeightPx = defaultH,
            cornerRadiusX = if (shapeType == ShapeType.CARD) 0f else null,
            cornerRadiusY = if (shapeType == ShapeType.CARD) 0f else null,
            strokeColorArgb = when {
                isLine -> lineStrokeColor
                else -> ShapeLabelDefaults.BORDER_COLOR_ARGB
            },
            strokeWidthPx = when {
                isLine -> 6f
                else -> ShapeLabelDefaults.BORDER_WIDTH_PX
            },
            viewport = EditorViewport(scale = 1f),
            appearance = EditorAppearance(shadowIntensity = 0f, alpha = 1f),
        )
    }

    @Deprecated("Use createShapeTextGroup for Phase 3 composite layers.")
    fun createShapeTextLayer(templateWidth: Float, shapeType: ShapeType): EditorLayer =
        buildHybridShapeTextLayer(templateWidth, shapeType)

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
            type = LayerType.SHAPE,
            text = "",
            textColorArgb = 0xFF000000.toInt(),
            textSizeSp = 28f,
            shapeType = shapeType,
            shapeColorArgb = if (isTextOnly || isLine) 0x00FFFFFF else 0xFFE3F2FD.toInt(),
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

    fun createTextLayer(templateWidth: Float, defaultText: String = "Nhập chữ..."): EditorLayer {
        return EditorLayer(
            type = LayerType.TEXT,
            text = defaultText,
            textColorArgb = 0xFF5B21B6.toInt(),
            textSizeSp = ShapeLabelDefaults.DEFAULT_TEXT_SIZE_SP,
            shapeType = ShapeType.TEXT_ONLY,
            shapeColorArgb = 0x00FFFFFF,
            shapeWidthPx = 60f,
            shapeHeightPx = 30f,
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
        shapeWidthPx = stickerWidth.toFloat(),
        shapeHeightPx = stickerHeight.toFloat(),
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
